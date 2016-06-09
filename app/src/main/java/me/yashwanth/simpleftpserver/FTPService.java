package me.yashwanth.simpleftpserver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;


import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class FTPService extends Service implements Runnable{

    private static final String TAG = FTPService.class.getSimpleName();
    private static int port = 2211;;
    // Service will (global) broadcast when server start/stop
    static public final String ACTION_STARTED = "me.yashwanth.simpleftpserver.FTPSERVER_STARTED";
    static public final String ACTION_STOPPED = "me.yashwanth.simpleftpserver.FTPSERVER_STOPPED";
    static public final String ACTION_FAILEDTOSTART = "me.yashwanth.simpleftpserver.FTPSERVER_FAILEDTOSTART";

    // RequestStartStopReceiver listens for these actions to start/stop this server
    static public final String ACTION_START_FTPSERVER = "me.yashwanth.simpleftpserver.ACTION_START_FTPSERVER";
    static public final String ACTION_STOP_FTPSERVER = "me.yashwanth.simpleftpserver.ACTION_STOP_FTPSERVER";

    protected boolean shouldExit = false;

    private FtpServer server;
    protected static Thread serverThread = null;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldExit = false;
        int attempts = 10;
        // The previous server thread may still be cleaning up, wait for it to finish.
        while (serverThread != null) {
            Log.w(TAG, "Won't start, server thread exists");
            if (attempts > 0) {
                attempts--;
                FTPService.sleepIgnoreInterupt(1000);
            } else {
                Log.w(TAG, "Server thread already exists");
                return START_STICKY;
            }
        }
        Log.d(TAG, "Creating server thread");
        serverThread = new Thread(this);
        serverThread.start();

        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void run() {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ConnectionConfigFactory connectionConfigFactory = new ConnectionConfigFactory();
        connectionConfigFactory.setAnonymousLoginEnabled(true);

        serverFactory.setConnectionConfig(connectionConfigFactory.createConnectionConfig());

        BaseUser user = new BaseUser();
        user.setName("anonymous");
        
        String path = Environment.getExternalStorageDirectory().getAbsolutePath();
        user.setHomeDirectory(path);
        List<Authority> list = new ArrayList<>();
        list.add(new WritePermission());
        user.setAuthorities(list);
        try {
            serverFactory.getUserManager().save(user);
        } catch (FtpException e) {
            e.printStackTrace();
        }
        ListenerFactory fac = new ListenerFactory();

        ServerSocket s = null;
        try {
            s = new ServerSocket(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        port = s.getLocalPort();
        for(int i=2211;i<65000;i++){
            if(isPortAvailable(i)) {
                port = i;
                break;
            }
        }
        fac.setPort(port);
//        port = fac.getPort();

        serverFactory.addListener("default",fac.createListener());
        try {
            server = serverFactory.createServer();
            if(isConnectedToLocalNetwork(getBaseContext()) && isConnectedUsingWifi(getBaseContext())){
                server.start();
                sendBroadcast(new Intent(FTPService.ACTION_STARTED));
            }
            else{
                sendBroadcast(new Intent(FTPService.ACTION_FAILEDTOSTART).putExtra("reason","Not Connected"));
                stopSelf();
            }
        } catch (FtpException e) {

            sendBroadcast(new Intent(FTPService.ACTION_FAILEDTOSTART));
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy() Stopping server");
        shouldExit = true;
        if (serverThread == null) {
            Log.w(TAG, "Stopping with null serverThread");
            return;
        }
        serverThread.interrupt();
        try {
            serverThread.join(10000); // wait 10 sec for server thread to finish
        } catch (InterruptedException e) {
        }
        if (serverThread.isAlive()) {
            Log.w(TAG, "Server thread failed to exit");
            // it may still exit eventually if we just leave the shouldExit flag set
        } else {
            Log.d(TAG, "serverThread join()ed ok");
            serverThread = null;
        }
        if(server!=null){
            server.stop();

            sendBroadcast(new Intent(FTPService.ACTION_STOPPED));
        }
        Log.d(TAG, "FTPServerService.onDestroy() finished");
    }

    public static boolean isRunning() {
        // return true if and only if a server Thread is running
        if (serverThread == null) {
            Log.d(TAG, "Server is not running (null serverThread)");
            return false;
        }
        if (!serverThread.isAlive()) {
            Log.d(TAG, "serverThread non-null but !isAlive()");
        } else {
            Log.d(TAG, "Server is alive");
        }
        return true;
    }
    public static void sleepIgnoreInterupt(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    public static boolean isConnectedToLocalNetwork(Context context) {
        boolean connected = false;
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        connected = ni != null
                && ni.isConnected()
                && (ni.getType() & (ConnectivityManager.TYPE_WIFI | ConnectivityManager.TYPE_ETHERNET)) != 0;
        if (!connected) {
            Log.d(TAG, "isConnectedToLocalNetwork: see if it is an WIFI AP");
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            try {
                Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
                connected = (Boolean) method.invoke(wm);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!connected) {
            Log.d(TAG, "isConnectedToLocalNetwork: see if it is an USB AP");
            try {
                for (NetworkInterface netInterface : Collections.list(NetworkInterface
                        .getNetworkInterfaces())) {
                    if (netInterface.getDisplayName().startsWith("rndis")) {
                        connected = true;
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        return connected;
    }

    /**
     * Checks to see if we are connected using wifi
     *
     * @return true if connected using wifi
     */
    public static boolean isConnectedUsingWifi(Context context) {

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected()
                && ni.getType() == ConnectivityManager.TYPE_WIFI;
    }

    public static InetAddress getLocalInetAddress(Context context) {
        if (!isConnectedToLocalNetwork(context)) {
            Log.e(TAG, "getLocalInetAddress called and no connection");
            return null;
        }

        if (isConnectedUsingWifi(context)) {

            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            if (ipAddress == 0)
                return null;
            return intToInet(ipAddress);
        }

        try {
            Enumeration<NetworkInterface> netinterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (netinterfaces.hasMoreElements()) {
                NetworkInterface netinterface = netinterfaces.nextElement();
                Enumeration<InetAddress> adresses = netinterface.getInetAddresses();
                while (adresses.hasMoreElements()) {
                    InetAddress address = adresses.nextElement();
                    // this is the condition that sometimes gives problems
                    if (!address.isLoopbackAddress()
                            && !address.isLinkLocalAddress())
                        return address;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    public static InetAddress intToInet(int value) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = byteOfInt(value, i);
        }
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // This only happens if the byte array has a bad length
            return null;
        }
    }
    public static byte byteOfInt(int value, int which) {
        int shift = which * 8;
        return (byte) (value >> shift);
    }

    public static int getPort(){
        return port;
    }
    public static boolean isPortAvailable(int port) {

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                /* should not be thrown */
                }
            }
        }

        return false;
    }
}

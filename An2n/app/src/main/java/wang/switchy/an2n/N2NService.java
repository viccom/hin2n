package wang.switchy.an2n;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

import wang.switchy.an2n.event.ErrorEvent;
import wang.switchy.an2n.event.StartEvent;
import wang.switchy.an2n.event.StopEvent;
import wang.switchy.an2n.model.EdgeCmd;
import wang.switchy.an2n.model.EdgeStatus;

/**
 * Created by janiszhang on 2018/4/15.
 */

// TODO: 2018/4/18 service 提高到前台

public class N2NService extends VpnService {

    public static N2NService INSTANCE;
    public static boolean sIsRunning = false;

    private ParcelFileDescriptor mParcelFileDescriptor = null;
    private EdgeCmd cmd;
    private boolean mStartResult;

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        Log.e("zhangbz", "N2NService onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.e("zhangbz", "N2NService onStartCommand");

        Builder b = new Builder();
        b.setMtu(1400 - 14);

        String ipAddress = intent.getStringExtra("ip_address");
        b.addAddress(ipAddress, 24);

        String[] split = ipAddress.split("\\.");//参数不能直接写成"."
        String route = split[0] + "." + split[1] + "." + split[2] + ".0";
        b.addRoute(route, 24);

        mParcelFileDescriptor = b.setSession("N2N_V2S")/*.setConfigureIntent(pendingIntent)*/.establish();

        cmd = new EdgeCmd();
        cmd.ipAddr = intent.getStringExtra("ip_address");
        cmd.ipNetmask = intent.getStringExtra("net_mask");
        cmd.supernodes = new String[2];
        cmd.supernodes[0] = intent.getStringExtra("super_node");
        cmd.supernodes[1] = "";//intent.getStringExtra("spare_super_node");
        cmd.community = intent.getStringExtra("community");
        cmd.encKey = intent.getStringExtra("encrypt");
        cmd.encKeyFile = null;
        cmd.macAddr = getRandomMac();
        cmd.mtu = 1400;
        cmd.localIP = "";
        cmd.holePunchInterval = 25;
        cmd.reResoveSupernodeIP = false;
        cmd.localPort = 0;
        cmd.allowRouting = false;
        cmd.dropMuticast = true;
        cmd.traceLevel = 4;//2;
        cmd.vpnFd = mParcelFileDescriptor.detachFd();

        try {
            // TODO: 2018/4/17 需要判断返回值
            mStartResult = startEdge(cmd);

            if (mStartResult) {
                sIsRunning = true;
                EventBus.getDefault().post(new StartEvent());
            } else {
                EventBus.getDefault().post(new ErrorEvent());
            }

        } catch (Exception e) {
            Log.e("zhangbz", e.getMessage());
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public void stop() {
        Log.e("zhangbz", "call stop");
        stopEdge();

        try {
            if (mParcelFileDescriptor != null) {
                mParcelFileDescriptor.close();
                mParcelFileDescriptor = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        sIsRunning = false;
        EventBus.getDefault().post(new StopEvent());
//        stopSelf();
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
//        Toast.makeText(INSTANCE, "N2NService onRevoke", Toast.LENGTH_SHORT).show();
        Log.e("zhangbz", "N2NService onRevoke");
        stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("zhangbz", "N2NService onDestroy");
//        stopEdge();
    }

    public native boolean startEdge(EdgeCmd cmd);

    public native void stopEdge();

    public native EdgeStatus getEdgeStatus();

    public void reportEdgeStatus(EdgeStatus status)
    {
    }


    private String getRandomMac() {
        String mac = "", hex="0123456789abcdef";
        Random rand = new Random();
        for (int i = 0; i < 17; ++i)
        {
            if ((i + 1) % 3 == 0) {
                mac += ':';
                continue;
            }
            mac += hex.charAt(rand.nextInt(16));
        }
        return mac;
    }

    private int getIpAddrPrefixLength(String netmask) {
        try {
            byte[] byteAddr = InetAddress.getByName(netmask).getAddress();
            int prefixLength = 0;
            for (int i =0; i < byteAddr.length; i++) {
                for (int j = 0; j < 8; j++) {
                    if ((byteAddr[i] << j & 0xFF) != 0) {
                        prefixLength++;
                    } else {
                        return prefixLength;
                    }
                }
            }
            return prefixLength;
        } catch (Exception e) {
            return -1;
        }
    }

    private String getRoute(String ipAddr, int prefixLength) {
        byte[] arr = {(byte)0x00, (byte)0x80, (byte)0xC0, (byte)0xE0, (byte)0xF0, (byte)0xF8, (byte)0xFC, (byte)0xFE, (byte)0xFF};

        if (prefixLength > 32 || prefixLength < 0) {
            return "";
        }
        try {
            byte[] byteAddr = InetAddress.getByName(ipAddr).getAddress();
            int idx = 0;
            while (prefixLength >= 8) {
                idx++;
                prefixLength -= 8;
            }
            if (idx < byteAddr.length) {
                byteAddr[idx++] &= arr[prefixLength];
            }
            for (; idx < byteAddr.length; idx++) {
                byteAddr[idx] = (byte)0x00;
            }
            return InetAddress.getByAddress(byteAddr).getHostAddress();
        } catch (Exception e) {
            return "";
        }
    }

}

package net.openvpn.openvpn.wg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.openvpn.openvpn.OpenVPNClient;
import net.openvpn.openvpn.R;

/**
 * โครง WireGuard VpnService
 * เฟสนี้: เตรียมสิทธิ์ + notification + เก็บ conf
 * ขั้นถัดไป: ต่อกับ GoBackend / Tunnel ของไลบรารี wireguard-android
 */
public class WgVpnService extends VpnService {
    private static final String TAG = "WgVpnService";
    public static final String ACTION_CONNECT = "net.openvpn.openvpn.wg.CONNECT";
    public static final String ACTION_DISCONNECT = "net.openvpn.openvpn.wg.DISCONNECT";
    public static final String EXTRA_PROFILE_ID = "profile_id";
    public static final String EXTRA_CONF = "conf";

    private static final String CHANNEL_ID = "wg_vpn_channel";
    private ParcelFileDescriptor tunFd;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_DISCONNECT.equals(action)) {
            disconnect();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CONNECT.equals(action)) {
            String conf = intent.getStringExtra(EXTRA_CONF);
            String profileId = intent.getStringExtra(EXTRA_PROFILE_ID);
            startForeground(42, buildNotification("กำลังเชื่อมต่อ WireGuard..."));
            connect(conf, profileId);
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void connect(String conf, String profileId) {
        try {
            if (conf == null || conf.trim().isEmpty()) {
                Log.e(TAG, "conf is empty");
                stopSelf();
                return;
            }
            WgConfigParser.ParsedConfig parsed = WgConfigParser.parse(conf);
            Log.i(TAG, "WG profile ok peers=" + parsed.peers.size()
                    + " address=" + parsed.iface.address);

            // TODO เฟสถัดไป: ใช้ com.wireguard.android.backend
            // Backend backend = new GoBackend(this);
            // Tunnel tunnel = ...
            // backend.setState(tunnel, Tunnel.State.UP, config);

            // โครง Builder (ยังไม่ขึ้นอุโมงค์จริงจนกว่าจะต่อ backend)
            Builder builder = new Builder();
            builder.setSession("VPN-THAI-WG");
            builder.setMtu(parsed.iface.mtu > 0 ? parsed.iface.mtu : 1280);

            // ตัวอย่าง parse Address แบบง่าย (IPv4 ก่อน)
            if (parsed.iface.address != null && !parsed.iface.address.isEmpty()) {
                String first = parsed.iface.address.split(",")[0].trim();
                String[] parts = first.split("/");
                String ip = parts[0].trim();
                int prefix = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32;
                if (ip.contains(":")) {
                    builder.addAddress(ip, prefix);
                } else {
                    builder.addAddress(ip, prefix);
                }
            }

            if (parsed.iface.dns != null && !parsed.iface.dns.isEmpty()) {
                for (String d : parsed.iface.dns.split(",")) {
                    String dns = d.trim();
                    if (!dns.isEmpty()) builder.addDnsServer(dns);
                }
            }

            // ยังไม่ establish จนกว่า backend WG พร้อม — กันระบบค้าง
            // tunFd = builder.establish();

            startForeground(42, buildNotification("WireGuard: โปรไฟล์พร้อม (รอต่อ backend)"));
            Log.i(TAG, "Phase1 skeleton ready for profile " + profileId);

        } catch (Exception e) {
            Log.e(TAG, "connect failed", e);
            startForeground(42, buildNotification("WireGuard error: " + e.getMessage()));
            stopSelf();
        }
    }

    private void disconnect() {
        try {
            if (tunFd != null) {
                tunFd.close();
                tunFd = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "disconnect", e);
        }
        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private Notification buildNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "WireGuard", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, OpenVPNClient.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return b.setContentTitle("VPN THAI")
                .setContentText(text)
                .setSmallIcon(R.drawable.icon)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
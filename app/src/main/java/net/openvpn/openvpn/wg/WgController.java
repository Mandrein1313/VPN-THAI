package net.openvpn.openvpn.wg;

import android.content.Context;
import android.util.Log;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.StringReader;

/** ควบคุมขึ้น/ลงอุโมงค์ WireGuard */
public class WgController {
    private static final String TAG = "WgController";
    private static WgController instance;

    private final Backend backend;
    private WgTunnel tunnel;
    private String activeProfileId;

    private WgController(Context context) {
        this.backend = new GoBackend(context.getApplicationContext());
    }

    public static synchronized WgController get(Context context) {
        if (instance == null) {
            instance = new WgController(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized void connect(String profileId, String confContent) throws Exception {
        if (confContent == null || confContent.trim().isEmpty()) {
            throw new IllegalArgumentException("conf ว่าง");
        }

        // ตรวจ syntax ก่อน
        WgConfigParser.parse(confContent);

        Config config;
        try (BufferedReader reader = new BufferedReader(new StringReader(confContent))) {
            config = Config.parse(reader);
        }

        // ปิดของเก่าถ้ามี
        disconnectQuiet();

        String safeName = sanitizeName(profileId != null ? profileId : "wg0");
        tunnel = new WgTunnel(safeName);
        activeProfileId = profileId;

        backend.setState(tunnel, Tunnel.State.UP, config);
        Log.i(TAG, "WireGuard UP profile=" + profileId);
    }

    public synchronized void disconnect() throws Exception {
        if (tunnel != null) {
            backend.setState(tunnel, Tunnel.State.DOWN, null);
            Log.i(TAG, "WireGuard DOWN profile=" + activeProfileId);
        }
        tunnel = null;
        activeProfileId = null;
    }

    public synchronized boolean isUp() {
        try {
            if (tunnel == null) return false;
            return backend.getState(tunnel) == Tunnel.State.UP;
        } catch (Exception e) {
            return false;
        }
    }

    public String getActiveProfileId() {
        return activeProfileId;
    }

    private void disconnectQuiet() {
        try {
            disconnect();
        } catch (Exception e) {
            Log.w(TAG, "disconnectQuiet", e);
        }
    }

    private static String sanitizeName(String name) {
        // ชื่อ tunnel อนุญาตเฉพาะ a-z A-Z 0-9 = + . - _
        String s = name.replaceAll("[^a-zA-Z0-9=+._-]", "_");
        if (s.length() > 15) s = s.substring(0, 15);
        if (s.isEmpty()) s = "wg0";
        return s;
    }
}
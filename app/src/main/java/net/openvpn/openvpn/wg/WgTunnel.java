package net.openvpn.openvpn.wg;

import android.util.Log;

import com.wireguard.android.backend.Tunnel;

/** Tunnel ชื่อเดียวสำหรับแอปนี้ */
public class WgTunnel implements Tunnel {
    private static final String TAG = "WgTunnel";
    private final String name;
    private State state = State.DOWN;

    public WgTunnel(String name) {
        this.name = name != null ? name : "wg0";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void onStateChange(State newState) {
        this.state = newState;
        Log.i(TAG, "state -> " + newState);
    }

    public State getState() {
        return state;
    }
}
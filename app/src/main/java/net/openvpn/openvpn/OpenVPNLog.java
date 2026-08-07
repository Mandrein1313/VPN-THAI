package net.openvpn.openvpn;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import net.openvpn.openvpn.OpenVPNService.LogMsg;

public class OpenVPNLog extends OpenVPNClientBase implements OnClickListener {
    private static final String TAG = "OpenVPNClientLog";

    private Button mPause;
    private Button mResume;
    private Button mClear;
    private ScrollView mScrollView;
    private TextView mTextView;
    private TextView mStatusLabel;
    private ArrayList<LogMsg> pause_buffer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log);

        mTextView = findViewById(R.id.log_textview);
        mScrollView = findViewById(R.id.log_scrollview);
        mPause = findViewById(R.id.log_pause);
        mResume = findViewById(R.id.log_resume);
        mClear = findViewById(R.id.log_clear);
        mStatusLabel = findViewById(R.id.log_status);

        if (mPause != null) mPause.setOnClickListener(this);
        if (mResume != null) mResume.setOnClickListener(this);
        if (mClear != null) mClear.setOnClickListener(this);

        if (mTextView != null) {
            mTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
            mTextView.setTextIsSelectable(true); // ให้คัดลอกข้อความได้
        }

        doBindService();
        updateStatusLabel();
    }

    private void updateStatusLabel() {
        if (mStatusLabel == null) return;

        boolean ovpnActive = is_active();
        boolean wgUp = false;
        try {
            wgUp = net.openvpn.openvpn.wg.WgController.get(this).isUp();
        } catch (Exception ignored) {}

        if (wgUp) {
            mStatusLabel.setText("สถานะ: WireGuard เชื่อมต่ออยู่");
            mStatusLabel.setTextColor(0xFF2E7D32); // เขียว
        } else if (ovpnActive) {
            mStatusLabel.setText("สถานะ: OpenVPN เชื่อมต่ออยู่");
            mStatusLabel.setTextColor(0xFF1565C0); // น้ำเงิน
        } else {
            mStatusLabel.setText("สถานะ: ยังไม่ได้เชื่อมต่อ");
            mStatusLabel.setTextColor(0xFF757575); // เทา
        }
    }

    private void set_pause_state(boolean paused) {
        if (paused) {
            if (mPause != null) mPause.setVisibility(View.GONE);
            if (mResume != null) mResume.setVisibility(View.VISIBLE);
            pause_buffer = new ArrayList<>();
        } else {
            if (mPause != null) mPause.setVisibility(View.VISIBLE);
            if (mResume != null) mResume.setVisibility(View.GONE);

            if (pause_buffer != null) {
                for (LogMsg lm : pause_buffer) {
                    if (mTextView != null) mTextView.append(lm.line);
                }
                scroll_textview_to_bottom();
                pause_buffer = null;
            }
        }
    }

    private void scroll_textview_to_bottom() {
        if (mScrollView == null || mTextView == null) return;
        mScrollView.post(() -> mScrollView.smoothScrollTo(0, mTextView.getBottom()));
    }

    private void refresh_log_view() {
        ArrayDeque<LogMsg> hist = log_history();
        if (hist == null || mTextView == null) return;

        StringBuilder builder = new StringBuilder();
        for (LogMsg lm : hist) {
            builder.append(lm.line);
        }
        mTextView.setText(builder.toString());
        scroll_textview_to_bottom();
    }

    private void clear_log() {
        if (mTextView != null) {
            mTextView.setText("");
        }
        pause_buffer = null;
        Toast.makeText(this, "ล้างบันทึกแล้ว", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.log_pause) {
            set_pause_state(true);
        } else if (id == R.id.log_resume) {
            set_pause_state(false);
        } else if (id == R.id.log_clear) {
            clear_log();
        }
    }

    public void log(LogMsg lm) {
        if (pause_buffer == null) {
            if (mTextView != null) {
                mTextView.append(lm.line);
                scroll_textview_to_bottom();
            }
        } else {
            pause_buffer.add(lm);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "LOG: onDestroy");
        doUnbindService();
        super.onDestroy();
    }

    @Override
    protected void post_bind() {
        Log.d(TAG, "LOG: post_bind");
        refresh_log_view();
        set_pause_state(false);
        updateStatusLabel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusLabel();
    }
}
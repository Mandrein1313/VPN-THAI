package net.openvpn.openvpn.wg;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** เก็บโปรไฟล์ WireGuard เป็นไฟล์ใน private storage */
public class WgProfileStore {
    private static final String TAG = "WgProfileStore";
    private static final String INDEX_FILE = "wg_profiles.json";
    private static final String DIR = "wg_configs";

    public static class Profile {
        public String id;
        public String name;
        public String confContent;
    }

    private final Context app;

    public WgProfileStore(Context context) {
        this.app = context.getApplicationContext();
        new File(app.getFilesDir(), DIR).mkdirs();
    }

    public synchronized List<Profile> list() {
        List<Profile> out = new ArrayList<>();
        try {
            File idx = new File(app.getFilesDir(), INDEX_FILE);
            if (!idx.exists()) return out;
            String json = readFile(idx);
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Profile p = new Profile();
                p.id = o.getString("id");
                p.name = o.getString("name");
                File f = configFile(p.id);
                if (f.exists()) {
                    p.confContent = readFile(f);
                    out.add(p);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "list failed", e);
        }
        return out;
    }

    public synchronized Profile importConf(String name, String content) throws Exception {
        WgConfigParser.parse(content); // validate

        Profile p = new Profile();
        p.id = "wg_" + System.currentTimeMillis();
        p.name = (name == null || name.trim().isEmpty()) ? p.id : name.trim();
        p.confContent = content;

        writeFile(configFile(p.id), content);

        List<Profile> all = list();
        // list() อ่านจาก index เก่า — สร้าง index ใหม่
        all = listRawIndex();
        all.add(p);
        saveIndex(all);
        return p;
    }

    public synchronized void delete(String id) {
        try {
            File f = configFile(id);
            if (f.exists()) f.delete();
            List<Profile> all = listRawIndex();
            List<Profile> next = new ArrayList<>();
            for (Profile p : all) {
                if (!p.id.equals(id)) next.add(p);
            }
            saveIndex(next);
        } catch (Exception e) {
            Log.e(TAG, "delete failed", e);
        }
    }

    public Profile get(String id) {
        for (Profile p : list()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    private List<Profile> listRawIndex() throws Exception {
        List<Profile> out = new ArrayList<>();
        File idx = new File(app.getFilesDir(), INDEX_FILE);
        if (!idx.exists()) return out;
        JSONArray arr = new JSONArray(readFile(idx));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Profile p = new Profile();
            p.id = o.getString("id");
            p.name = o.getString("name");
            out.add(p);
        }
        return out;
    }

    private void saveIndex(List<Profile> profiles) throws Exception {
        JSONArray arr = new JSONArray();
        for (Profile p : profiles) {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("name", p.name);
            arr.put(o);
        }
        writeFile(new File(app.getFilesDir(), INDEX_FILE), arr.toString(2));
    }

    private File configFile(String id) {
        return new File(new File(app.getFilesDir(), DIR), id + ".conf");
    }

    private static String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        int n = in.read(buf);
        in.close();
        return new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8);
    }

    private static void writeFile(File f, String content) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.close();
    }
}
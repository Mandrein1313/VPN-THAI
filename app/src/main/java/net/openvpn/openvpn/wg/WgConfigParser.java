package net.openvpn.openvpn.wg;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser ง่าย ๆ สำหรับไฟล์ WireGuard .conf มาตรฐาน
 * รองรับ [Interface] และ [Peer] พื้นฐาน
 */
public class WgConfigParser {

    public static class InterfaceBlock {
        public String privateKey = "";
        public String address = "";
        public String dns = "";
        public int mtu = 0;
        public String listenPort = "";
    }

    public static class PeerBlock {
        public String publicKey = "";
        public String presharedKey = "";
        public String endpoint = "";
        public String allowedIps = "0.0.0.0/0, ::/0";
        public int persistentKeepalive = 0;
    }

    public static class ParsedConfig {
        public InterfaceBlock iface = new InterfaceBlock();
        public List<PeerBlock> peers = new ArrayList<>();
        public String raw = "";
    }

    public static ParsedConfig parse(String content) throws IllegalArgumentException {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("ไฟล์ว่าง");
        }

        ParsedConfig cfg = new ParsedConfig();
        cfg.raw = content;

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        String section = "";
        PeerBlock currentPeer = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;

            if (line.equalsIgnoreCase("[Interface]")) {
                section = "interface";
                currentPeer = null;
                continue;
            }
            if (line.equalsIgnoreCase("[Peer]")) {
                section = "peer";
                currentPeer = new PeerBlock();
                cfg.peers.add(currentPeer);
                continue;
            }

            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            if ("interface".equals(section)) {
                switch (key.toLowerCase()) {
                    case "privatekey":
                        cfg.iface.privateKey = value;
                        break;
                    case "address":
                        cfg.iface.address = value;
                        break;
                    case "dns":
                        cfg.iface.dns = value;
                        break;
                    case "mtu":
                        try { cfg.iface.mtu = Integer.parseInt(value); } catch (Exception ignored) {}
                        break;
                    case "listenport":
                        cfg.iface.listenPort = value;
                        break;
                }
            } else if ("peer".equals(section) && currentPeer != null) {
                switch (key.toLowerCase()) {
                    case "publickey":
                        currentPeer.publicKey = value;
                        break;
                    case "presharedkey":
                        currentPeer.presharedKey = value;
                        break;
                    case "endpoint":
                        currentPeer.endpoint = value;
                        break;
                    case "allowedips":
                        currentPeer.allowedIps = value;
                        break;
                    case "persistentkeepalive":
                        try { currentPeer.persistentKeepalive = Integer.parseInt(value); } catch (Exception ignored) {}
                        break;
                }
            }
        }

        if (cfg.iface.privateKey.isEmpty()) {
            throw new IllegalArgumentException("ไม่พบ PrivateKey ใน [Interface]");
        }
        if (cfg.peers.isEmpty()) {
            throw new IllegalArgumentException("ไม่พบ [Peer]");
        }
        for (PeerBlock p : cfg.peers) {
            if (p.publicKey.isEmpty()) {
                throw new IllegalArgumentException("Peer ต้องมี PublicKey");
            }
        }
        return cfg;
    }

    /** ดึงชื่อจากชื่อไฟล์ */
    public static String nameFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) return "wireguard";
        String n = filename;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        if (n.toLowerCase().endsWith(".conf")) {
            n = n.substring(0, n.length() - 5);
        }
        return n.isEmpty() ? "wireguard" : n;
    }
}

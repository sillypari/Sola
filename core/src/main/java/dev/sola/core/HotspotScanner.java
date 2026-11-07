package dev.sola.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Method 1: Mobile Hotspot and Subnet Auto-Discovery.
 * Scans local hotspot subnets (192.168.43.x, 192.168.44.x, 192.168.50.x, or current LAN)
 * to automatically discover the Ola S1 scooter daemon on port 50005.
 */
public final class HotspotScanner {

    public interface ScanCallback {
        void onProgress(int scanned, int total);
        void onFound(String ipAddress);
        void onComplete(boolean found);
    }

    private static final int PORT = 50005;
    private static final int TIMEOUT_MS = 250;

    /**
     * Attempts to find the scooter on known hotspot/LAN subnets asynchronously.
     */
    public static void discoverScooter(ScanCallback callback) {
        new Thread(() -> {
            List<String> candidates = getCandidateSubnets();
            AtomicBoolean found = new AtomicBoolean(false);

            // First check ARP table for known connected devices
            List<String> arpIps = readArpTableIps();
            for (String ip : arpIps) {
                if (testPort(ip, PORT, 300)) {
                    found.set(true);
                    if (callback != null) {
                        callback.onFound(ip);
                        callback.onComplete(true);
                    }
                    return;
                }
            }

            // Next scan subnets
            for (String subnet : candidates) {
                if (found.get()) break;
                scanSubnet(subnet, found, callback);
            }

            if (!found.get() && callback != null) {
                callback.onComplete(false);
            }
        }, "HotspotScannerThread").start();
    }

    private static void scanSubnet(String subnetPrefix, AtomicBoolean found, ScanCallback callback) {
        ExecutorService pool = Executors.newFixedThreadPool(32);
        int total = 254;

        for (int i = 1; i <= 254; i++) {
            final String targetIp = subnetPrefix + "." + i;
            final int index = i;

            pool.execute(() -> {
                if (found.get()) return;

                if (testPort(targetIp, PORT, TIMEOUT_MS)) {
                    if (found.compareAndSet(false, true)) {
                        if (callback != null) {
                            callback.onFound(targetIp);
                            callback.onComplete(true);
                        }
                    }
                }

                if (callback != null && index % 16 == 0) {
                    callback.onProgress(index, total);
                }
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(3500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {}
    }

    private static boolean testPort(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Inspects active network interfaces to find the phone's hotspot or LAN subnet.
     */
    public static List<String> getCandidateSubnets() {
        List<String> subnets = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface iface : Collections.list(interfaces)) {
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.contains(".") && !ip.startsWith("127.")) {
                            int lastDot = ip.lastIndexOf('.');
                            if (lastDot > 0) {
                                String prefix = ip.substring(0, lastDot);
                                if (!subnets.contains(prefix)) {
                                    subnets.add(prefix);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Common hotspot subnets if not already listed
        String[] defaults = new String[]{"192.168.43", "192.168.44", "192.168.50", "192.168.1"};
        for (String def : defaults) {
            if (!subnets.contains(def)) {
                subnets.add(def);
            }
        }

        return subnets;
    }

    private static List<String> readArpTableIps() {
        List<String> ips = new ArrayList<>();
        File arp = new File("/proc/net/arp");
        if (arp.exists() && arp.canRead()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(arp))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 4 && !tokens[0].equals("IP") && !tokens[3].equals("00:00:00:00:00:00")) {
                        ips.add(tokens[0]);
                    }
                }
            } catch (Exception ignored) {}
        }
        return ips;
    }
}


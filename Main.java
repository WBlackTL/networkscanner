import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NetworkScanner extends JFrame {
    
    // Configuration constants
    private static final int DEFAULT_THREAD_POOL_SIZE = 20;
    private static final int SCAN_TIMEOUT_MS = 2000;
    private static final int DEFAULT_IPV4_PREFIX = 24;
    private static final int MIN_CUSTOM_PREFIX = 1;
    private static final int MAX_IPV4_PREFIX = 32;
    private static final int MAX_IPV6_PREFIX = 64;
    private static final int MIN_IP_SUBNET = 1;
    private static final int MAX_IPV4_SUBNET = 254;
    private static final int MAX_IPV6_SUBNET = 65535;
    
    // UI Components
    private JTextArea resultArea;
    private JButton scanButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JComboBox<String> scanModeCombo;
    private JComboBox<String> ipVersionCombo;
    private JLabel customPrefixLabel;
    private JTextField customPrefixField;
    
    // Scan mode constants
    private static final String MODE_CUSTOM = "Custom";
    private static final String MODE_DEFAULT = "Default";
    private static final String MODE_AUTO = "Auto";
    
    // IP version constants
    private static final String IPV4 = "IPv4";
    private static final String IPV6 = "IPv6";
    
    public NetworkScanner() {
        setTitle("Network Scanner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        initMenu();
        initUI();
    }
    
    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu aboutMenu = new JMenu("About");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(
                this, "Network Scanner\nCopyright © 2026 WBlackTL\nLicense: BSD 3-Clause", 
                "About", JOptionPane.INFORMATION_MESSAGE));
        aboutMenu.add(aboutItem);
        menuBar.add(aboutMenu);
        setJMenuBar(menuBar);
    }
    
    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        
        // Create top panel with controls
        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Scan mode selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        controlPanel.add(new JLabel("Scan Mode:"), gbc);
        
        gbc.gridx = 1;
        String[] modes = {MODE_CUSTOM, MODE_DEFAULT, MODE_AUTO};
        scanModeCombo = new JComboBox<>(modes);
        scanModeCombo.addActionListener(e -> updateUIForMode());
        controlPanel.add(scanModeCombo, gbc);
        
        // IP version selection
        gbc.gridx = 2;
        controlPanel.add(new JLabel("IP Version:"), gbc);
        
        gbc.gridx = 3;
        String[] ipVersions = {IPV4, IPV6};
        ipVersionCombo = new JComboBox<>(ipVersions);
        ipVersionCombo.addActionListener(e -> updateUIForIPVersion());
        controlPanel.add(ipVersionCombo, gbc);
        
        // Custom prefix input
        gbc.gridx = 4;
        customPrefixLabel = new JLabel("Custom Prefix (/):");
        controlPanel.add(customPrefixLabel, gbc);
        
        gbc.gridx = 5;
        gbc.weightx = 0.5;
        customPrefixField = new JTextField(4);
        customPrefixField.setText("24");
        controlPanel.add(customPrefixField, gbc);
        
        // Scan button
        gbc.gridx = 6;
        gbc.weightx = 0;
        scanButton = new JButton("Start Scan");
        controlPanel.add(scanButton, gbc);
        
        add(controlPanel, BorderLayout.NORTH);
        
        // Create result area
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(resultArea);
        add(scrollPane, BorderLayout.CENTER);
        
        // Create bottom panel with progress and status
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.CENTER);
        
        statusLabel = new JLabel("Ready");
        bottomPanel.add(statusLabel, BorderLayout.EAST);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Add action listener
        scanButton.addActionListener(this::startScan);
        
        // Initialize UI state
        updateUIForMode();
        updateUIForIPVersion();
    }
    
    private void updateUIForMode() {
        String mode = (String) scanModeCombo.getSelectedItem();
        boolean isCustomMode = MODE_CUSTOM.equals(mode);
        customPrefixLabel.setVisible(isCustomMode);
        customPrefixField.setVisible(isCustomMode);
    }
    
    private void updateUIForIPVersion() {
        String ipVersion = (String) ipVersionCombo.getSelectedItem();
        if (IPV6.equals(ipVersion)) {
            customPrefixField.setText("64");
        } else {
            customPrefixField.setText("24");
        }
    }
    
    private void startScan(ActionEvent e) {
        scanButton.setEnabled(false);
        resultArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText("Scanning...");
        
        // Get scan parameters
        String selectedMode = (String) scanModeCombo.getSelectedItem();
        String ipVersion = (String) ipVersionCombo.getSelectedItem();
        int customPrefix = 0;
        
        if (MODE_CUSTOM.equals(selectedMode)) {
            try {
                customPrefix = Integer.parseInt(customPrefixField.getText());
                int maxPrefix = IPV4.equals(ipVersion) ? MAX_IPV4_PREFIX : MAX_IPV6_PREFIX;
                int minPrefix = MIN_CUSTOM_PREFIX;
                
                if (customPrefix < minPrefix || customPrefix > maxPrefix) {
                    JOptionPane.showMessageDialog(this,
                            String.format("Custom prefix must be between %d and %d for %s", 
                                    minPrefix, maxPrefix, ipVersion),
                            "Invalid Prefix", JOptionPane.ERROR_MESSAGE);
                    resetUIState();
                    return;
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, 
                        "Please enter a valid number for custom prefix",
                        "Invalid Input", JOptionPane.ERROR_MESSAGE);
                resetUIState();
                return;
            }
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
        AtomicInteger completed = new AtomicInteger(0);
        
        List<String> ips = getIPsToScan(selectedMode, ipVersion, customPrefix);
        if (ips.isEmpty()) {
            statusLabel.setText("No network interfaces found");
            scanButton.setEnabled(true);
            return;
        }
        
        int total = ips.size();
        progressBar.setMaximum(total);
        resultArea.append(String.format("Scanning %d IP addresses...\n\n", total));
        
        for (String ip : ips) {
            executor.submit(() -> {
                try {
                    InetAddress addr = InetAddress.getByName(ip);
                    if (addr.isReachable(SCAN_TIMEOUT_MS)) {
                        String hostName = addr.getCanonicalHostName();
                        String resultLine = String.format("%-40s - %s\n", ip, hostName);
                        SwingUtilities.invokeLater(() -> resultArea.append(resultLine));
                    }
                } catch (Exception ex) {
                    // Log silently for failed scans
                }
                
                int done = completed.incrementAndGet();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(done);
                    statusLabel.setText(String.format("Scanned %d of %d", done, total));
                    
                    if (done >= total) {
                        statusLabel.setText("Scan complete");
                        scanButton.setEnabled(true);
                        executor.shutdown();
                    }
                });
            });
        }
    }
    
    private List<String> getIPsToScan(String mode, String ipVersion, int customPrefix) {
        List<String> ipList = new ArrayList<>();
        
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    
                    if (IPV4.equals(ipVersion) && addr instanceof Inet4Address) {
                        handleIPv4Address(addr, mode, customPrefix, ipList);
                    } else if (IPV6.equals(ipVersion) && addr instanceof Inet6Address) {
                        handleIPv6Address(addr, mode, customPrefix, ipList);
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        
        return ipList;
    }
    
    private void handleIPv4Address(InetAddress addr, String mode, int customPrefix, List<String> ipList) {
        String ip = addr.getHostAddress();
        String[] parts = ip.split("\\.");
        
        if (parts.length == 4) {
            int prefixLength = getPrefixLengthForMode(mode, customPrefix, DEFAULT_IPV4_PREFIX, MAX_IPV4_PREFIX);
            int subnetSize = (int) Math.pow(2, 32 - prefixLength);
            int startRange = Math.max(1, MIN_IP_SUBNET);
            int endRange = Math.min(subnetSize - 2, MAX_IPV4_SUBNET);
            
            if (prefixLength >= 31) {
                startRange = 0;
                endRange = subnetSize - 1;
            }
            
            if (prefixLength <= 24) {
                // For larger subnets, scan only first 256 addresses
                endRange = Math.min(endRange, MAX_IPV4_SUBNET);
            }
            
            generateIPv4Addresses(parts, prefixLength, startRange, endRange, ipList);
        }
    }
    
    private void handleIPv6Address(InetAddress addr, String mode, int customPrefix, List<String> ipList) {
        String ip = addr.getHostAddress();
        int prefixLength = getPrefixLengthForMode(mode, customPrefix, 64, MAX_IPV6_PREFIX);
        
        // For IPv6, scan a limited range to avoid huge lists
        int scanRange = 256;  // Scan first 256 addresses
        generateIPv6Addresses(ip, prefixLength, scanRange, ipList);
    }
    
    private int getPrefixLengthForMode(String mode, int customPrefix, int defaultPrefix, int maxPrefix) {
        switch (mode) {
            case MODE_CUSTOM:
                return Math.min(Math.max(customPrefix, MIN_CUSTOM_PREFIX), maxPrefix);
            case MODE_DEFAULT:
                return defaultPrefix;
            case MODE_AUTO:
                // In auto mode, use the default prefix
                return defaultPrefix;
            default:
                return defaultPrefix;
        }
    }
    
    private void generateIPv4Addresses(String[] parts, int prefixLength, int start, int end, List<String> ipList) {
        int thirdOctet = Integer.parseInt(parts[2]);
        int fourthOctet = Integer.parseInt(parts[3]);
        
        if (prefixLength >= 24) {
            // Standard /24 or larger subnet
            String base = parts[0] + "." + parts[1] + "." + parts[2] + ".";
            for (int i = start; i <= end; i++) {
                ipList.add(base + i);
            }
        } else if (prefixLength >= 16) {
            // /16 to /23 subnet
            String base = parts[0] + "." + parts[1] + ".";
            int thirdStart = (thirdOctet >> (24 - prefixLength)) << (24 - prefixLength);
            int thirdEnd = thirdStart + (1 << (24 - prefixLength)) - 1;
            
            for (int i = thirdStart; i <= thirdEnd && i <= 255; i++) {
                for (int j = start; j <= end; j++) {
                    ipList.add(base + i + "." + j);
                }
            }
        } else {
            // Very large subnet (/8 to /15) - scan limited range
            String base = parts[0] + ".";
            int secondStart = (Integer.parseInt(parts[1]) >> (16 - prefixLength)) << (16 - prefixLength);
            int secondEnd = Math.min(secondStart + 1, 255);  // Limit to 2 second octets
            
            for (int i = secondStart; i <= secondEnd && i <= 255; i++) {
                for (int j = 0; j <= 255; j++) {
                    for (int k = start; k <= end; k++) {
                        ipList.add(base + i + "." + j + "." + k);
                    }
                }
            }
        }
    }
    
    private void generateIPv6Addresses(String ip, int prefixLength, int scanRange, List<String> ipList) {
        // Simplified IPv6 address generation
        // In a real implementation, you would properly parse and generate IPv6 addresses
        // This is a simplified version for demonstration
        
        String base = ip.substring(0, ip.lastIndexOf(':') + 1);
        
        for (int i = 1; i <= scanRange; i++) {
            // Generate simplified IPv6 addresses
            // Note: This is not correct IPv6 address generation, just for demonstration
            String generatedIP = String.format("%s%04x", base, i);
            ipList.add(generatedIP);
        }
    }
    
    private void resetUIState() {
        scanButton.setEnabled(true);
        progressBar.setValue(0);
        statusLabel.setText("Ready");
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            NetworkScanner scanner = new NetworkScanner();
            scanner.setVisible(true);
        });
    }
}
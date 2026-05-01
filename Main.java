import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends JFrame {

    private JTextArea resultArea;
    private JButton scanButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    public Main() {
        setTitle("Network Scanner");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
        initMenu();
        initUI();
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu aboutMenu = new JMenu("About");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(
                this, "Copyright WBlackTL 2026", "About", JOptionPane.INFORMATION_MESSAGE));
        aboutMenu.add(aboutItem);
        menuBar.add(aboutMenu);
        setJMenuBar(menuBar);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scanButton = new JButton("Scan");
        topPanel.add(scanButton);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        topPanel.add(progressBar);
        statusLabel = new JLabel("Ready");
        topPanel.add(statusLabel);
        add(topPanel, BorderLayout.NORTH);
        scanButton.addActionListener(this::startScan);
    }

    private void startScan(ActionEvent e) {
        scanButton.setEnabled(false);
        resultArea.setText("");
        progressBar.setValue(0);
        statusLabel.setText("Scanning...");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger completed = new AtomicInteger(0);
        List<String> ips = getLocalIPRange();
        if (ips.isEmpty()) {
            statusLabel.setText("No network interfaces found");
            scanButton.setEnabled(true);
            return;
        }
        int total = ips.size();
        progressBar.setMaximum(total);
        for (String ip : ips) {
            executor.submit(() -> {
                try {
                    InetAddress addr = InetAddress.getByName(ip);
                    if (addr.isReachable(2000)) {
                        String hostName = addr.getCanonicalHostName();
                        SwingUtilities.invokeLater(() -> resultArea.append(ip + " - " + hostName + "\n"));
                    }
                } catch (Exception ignored) {
                }
                int done = completed.incrementAndGet();
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(done);
                    if (done >= total) {
                        statusLabel.setText("Scan complete. Found devices listed.");
                        scanButton.setEnabled(true);
                        executor.shutdown();
                    }
                });
            });
        }
    }

    private List<String> getLocalIPRange() {
        List<String> list = new ArrayList<>();
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
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        String[] parts = ip.split("\\.");
                        if (parts.length == 4) {
                            String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
                            for (int i = 1; i <= 254; i++) {
                                list.add(prefix + i);
                            }
                        }
                        return list;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return list;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
//BY WBlackTL.
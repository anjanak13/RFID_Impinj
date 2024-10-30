import javax.swing.*;
import com.impinj.octane.*;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RFIDReaderGUI {
    private ImpinjReader reader1;
    private ImpinjReader reader2;
    private JTextArea outputArea1; // Output area for Reader 1
    private JTextArea outputArea2; // Output area for Reader 2
    private JButton startButton;
    private JButton stopButton;
    private JButton clearButton; // Clear button
    private JSlider txPowerSlider;
    private JSlider rxSensitivitySlider;
    private JCheckBox filterCheckbox; // Checkbox to filter EPCs
    private JLabel connectionIndicator1; // Connection indicator for Reader 1
    private JLabel connectionIndicator2; // Connection indicator for Reader 2

    public RFIDReaderGUI() {
        JFrame frame = new JFrame("RFID Reader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);

        // Output areas for each reader
        outputArea1 = new JTextArea();
        outputArea1.setEditable(false);
        JScrollPane scrollPane1 = new JScrollPane(outputArea1);

        outputArea2 = new JTextArea();
        outputArea2.setEditable(false);
        JScrollPane scrollPane2 = new JScrollPane(outputArea2);

        startButton = new JButton("Start Reading");
        stopButton = new JButton("Stop Reading");
        clearButton = new JButton("Clear Output"); // Initialize the clear button

        startButton.addActionListener(e -> startReading());
        stopButton.addActionListener(e -> stopReading());
        clearButton.addActionListener(e -> clearOutput()); // Add action listener to clear button

        // Slider for Transmit Power
        txPowerSlider = new JSlider(-30, 0, -5); // Range from -30 dBm to 0 dBm
        txPowerSlider.setMajorTickSpacing(5);
        txPowerSlider.setPaintTicks(true);
        txPowerSlider.setPaintLabels(true);

        // Slider for Receive Sensitivity
        rxSensitivitySlider = new JSlider(-120, -60, -85); // Range from -120 dBm to -60 dBm
        rxSensitivitySlider.setMajorTickSpacing(10);
        rxSensitivitySlider.setPaintTicks(true);
        rxSensitivitySlider.setPaintLabels(true);

        // Checkbox for filtering EPCs
        filterCheckbox = new JCheckBox("Filter EPCs with more than 9 characters");

        JPanel panel = new JPanel();
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(clearButton); // Add clear button to the panel

        // Add sliders and checkbox to the panel
        panel.add(new JLabel("Transmit Power (dBm):"));
        panel.add(txPowerSlider);
        panel.add(new JLabel("Receive Sensitivity (dBm):"));
        panel.add(rxSensitivitySlider);
        panel.add(filterCheckbox); // Add checkbox to panel

        // Create a panel to hold the output areas side by side
        JPanel outputPanel = new JPanel(new GridLayout(1, 2)); // 1 row, 2 columns
        outputPanel.add(scrollPane1); // Add scrollPane for Reader 1
        outputPanel.add(scrollPane2); // Add scrollPane for Reader 2

        // Connection indicators
        connectionIndicator1 = createConnectionIndicator();
        connectionIndicator2 = createConnectionIndicator();

        JPanel indicatorPanel = new JPanel();
        indicatorPanel.add(new JLabel("Reader 1: "));
        indicatorPanel.add(connectionIndicator1);
        indicatorPanel.add(new JLabel("Reader 2: "));
        indicatorPanel.add(connectionIndicator2);

        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
        frame.getContentPane().add(indicatorPanel); // Add the indicator panel to the frame
        frame.getContentPane().add(outputPanel); // Add the output panel to the frame
        frame.getContentPane().add(panel);

        frame.setVisible(true);

        // Display the headers for columns for each output area
        appendOutput(outputArea1, String.format("%-15s %-10s %s", "Tag #", "Date", "Time")); // Column headers for Reader 1
        appendOutput(outputArea2, String.format("%-15s %-10s %s", "Tag #", "Date", "Time")); // Column headers for Reader 2
    }

    private JLabel createConnectionIndicator() {
        JLabel indicator = new JLabel();
        indicator.setOpaque(true);
        indicator.setBackground(Color.RED); // Set default to red (disconnected)
        indicator.setPreferredSize(new java.awt.Dimension(20, 20)); // Set size of the indicator
        return indicator;
    }

    private void startReading() {
        // Start each reader in its own thread
        new Thread(() -> startReader("192.168.10.1", outputArea1, connectionIndicator1)).start(); // Reader 1
        new Thread(() -> startReader("192.168.10.2", outputArea2, connectionIndicator2)).start(); // Reader 2
    }

    private void startReader(String hostname, JTextArea outputArea, JLabel connectionIndicator) {
        ImpinjReader reader = new ImpinjReader();
        try {
            reader.connect(hostname);
            connectionIndicator.setBackground(Color.GREEN); // Change indicator to green
            appendOutput(outputArea, "Connected to the reader: " + hostname);

            // Set up the settings
            Settings settings = reader.queryDefaultSettings();
            AntennaConfigGroup antennas = settings.getAntennas();
            antennas.disableAll();
            antennas.enableById(new short[]{1});

            // Get values from sliders
            double txPower = txPowerSlider.getValue(); // Get value from the transmit power slider
            double rxSensitivity = rxSensitivitySlider.getValue(); // Get value from the receive sensitivity slider

            // Set transmit power and receive sensitivity based on slider values
            antennas.getAntenna(1).setTxPowerinDbm(txPower);
            antennas.getAntenna(1).setRxSensitivityinDbm(rxSensitivity);
            appendOutput(outputArea, "Transmit Power: " + txPower + " dBm for " + hostname);
            appendOutput(outputArea, "Receive Sensitivity: " + rxSensitivity + " dBm for " + hostname);

            settings.getReport().setMode(ReportMode.Individual);
            settings.getReport().setIncludeAntennaPortNumber(true);
            reader.applySettings(settings);
            appendOutput(outputArea, "Settings applied for " + hostname + ".");

            // Set the tag report listener
            reader.setTagReportListener((r, report) -> {
                for (Tag tag : report.getTags()) {
                    String epc = tag.getEpc().toString();
                    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date()); // Get current date
                    String time = new SimpleDateFormat("HH:mm:ss").format(new Date()); // Get current time
                    // Check if filtering is enabled
                    if (!filterCheckbox.isSelected() || epc.length() <= 9) {
                        appendOutput(outputArea, String.format("%-15s %-10s %s", epc, date, time)); // Format the output into columns
                    }
                }
            });

            // Start the reader
            reader.start();
            appendOutput(outputArea, "Reading started on " + hostname + ". Press Stop Reading to stop.");

            // Keep the application running
            Thread.sleep(Long.MAX_VALUE);
        } catch (OctaneSdkException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.stop();
                    reader.disconnect();
                    connectionIndicator.setBackground(Color.RED); // Change indicator to red
                    appendOutput(outputArea, "Disconnected from the reader.");
                }
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopReading() {
        if (reader1 != null) {
            try {
                reader1.stop();
                reader1.disconnect();
                connectionIndicator1.setBackground(Color.RED); // Change indicator to red
                appendOutput(outputArea1, "Disconnected from Reader 1.");
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
        if (reader2 != null) {
            try {
                reader2.stop();
                reader2.disconnect();
                connectionIndicator2.setBackground(Color.RED); // Change indicator to red
                appendOutput(outputArea2, "Disconnected from Reader 2.");
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
    }

    private void appendOutput(JTextArea outputArea, String message) {
        outputArea.append(message + "\n");
        // Auto-scroll to the bottom of the text area
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void clearOutput() {
        outputArea1.setText(""); // Clear the output area for Reader 1
        outputArea2.setText(""); // Clear the output area for Reader 2
        // Display the headers for columns again
        appendOutput(outputArea1, String.format("%-15s %-10s %s", "EPC", "Date", "Time")); // Column headers for Reader 1
        appendOutput(outputArea2, String.format("%-15s %-10s %s", "EPC", "Date", "Time")); // Column headers for Reader 2
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RFIDReaderGUI::new);
    }
}

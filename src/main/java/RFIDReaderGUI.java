import javax.swing.*;
import com.impinj.octane.*;
import com.google.cloud.firestore.Firestore;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import java.util.HashMap;
import java.util.Map;

import com.google.cloud.firestore.WriteResult;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RFIDReaderGUI {
    private ImpinjReader reader1;
    private ImpinjReader reader2;
    private JTextArea outputArea1;
    private JTextArea outputArea2;
    private JButton startButton;
    private JButton stopButton;
    private JButton clearButton;
    private JSlider txPowerSlider;
    private JSlider rxSensitivitySlider;
    private JCheckBox filterCheckbox;
    private JLabel connectionIndicator1;
    private JLabel connectionIndicator2;

    public RFIDReaderGUI() {
        FirebaseInitializer.initializeFirebase(); // Initialize Firebase

        JFrame frame = new JFrame("RFID Reader");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);

        outputArea1 = new JTextArea();
        outputArea1.setEditable(false);
        JScrollPane scrollPane1 = new JScrollPane(outputArea1);

        outputArea2 = new JTextArea();
        outputArea2.setEditable(false);
        JScrollPane scrollPane2 = new JScrollPane(outputArea2);

        startButton = new JButton("Start Reading");
        stopButton = new JButton("Stop Reading");
        clearButton = new JButton("Clear Output");

        startButton.addActionListener(e -> startReading());
        stopButton.addActionListener(e -> stopReading());
        clearButton.addActionListener(e -> clearOutput());

        txPowerSlider = new JSlider(-30, 0, -5);
        txPowerSlider.setMajorTickSpacing(5);
        txPowerSlider.setPaintTicks(true);
        txPowerSlider.setPaintLabels(true);

        rxSensitivitySlider = new JSlider(-120, -60, -85);
        rxSensitivitySlider.setMajorTickSpacing(10);
        rxSensitivitySlider.setPaintTicks(true);
        rxSensitivitySlider.setPaintLabels(true);

        filterCheckbox = new JCheckBox("Filter EPCs with more than 9 characters");

        JPanel panel = new JPanel();
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(clearButton);

        panel.add(new JLabel("Transmit Power (dBm):"));
        panel.add(txPowerSlider);
        panel.add(new JLabel("Receive Sensitivity (dBm):"));
        panel.add(rxSensitivitySlider);
        panel.add(filterCheckbox);

        JPanel outputPanel = new JPanel(new GridLayout(1, 2));
        outputPanel.add(scrollPane1);
        outputPanel.add(scrollPane2);

        connectionIndicator1 = createConnectionIndicator();
        connectionIndicator2 = createConnectionIndicator();

        JPanel indicatorPanel = new JPanel();
        indicatorPanel.add(new JLabel("Reader 1: "));
        indicatorPanel.add(connectionIndicator1);
        indicatorPanel.add(new JLabel("Reader 2: "));
        indicatorPanel.add(connectionIndicator2);

        frame.getContentPane().setLayout(new BoxLayout(frame.getContentPane(), BoxLayout.Y_AXIS));
        frame.getContentPane().add(indicatorPanel);
        frame.getContentPane().add(outputPanel);
        frame.getContentPane().add(panel);

        frame.setVisible(true);

        appendOutput(outputArea1, String.format("%-15s %-10s %s", "Tag #", "Date", "Time"));
        appendOutput(outputArea2, String.format("%-15s %-10s %s", "Tag #", "Date", "Time"));
    }

    private JLabel createConnectionIndicator() {
        JLabel indicator = new JLabel();
        indicator.setOpaque(true);
        indicator.setBackground(Color.RED);
        indicator.setPreferredSize(new Dimension(20, 20));
        return indicator;
    }

    private void startReading() {
        if (reader1 == null) reader1 = new ImpinjReader();
        if (reader2 == null) reader2 = new ImpinjReader();

        new Thread(() -> startReader(reader1, "192.168.10.1", outputArea1, connectionIndicator1)).start();
        new Thread(() -> startReader(reader2, "192.168.10.2", outputArea2, connectionIndicator2)).start();
    }

    private void startReader(ImpinjReader reader, String hostname, JTextArea outputArea, JLabel connectionIndicator) {
        try {
            reader.connect(hostname);
            connectionIndicator.setBackground(Color.GREEN);
            appendOutput(outputArea, "Connected to the reader: " + hostname);

            Thread.sleep(100);

            Settings settings = reader.queryDefaultSettings();
            AntennaConfigGroup antennas = settings.getAntennas();
            antennas.disableAll();
            antennas.enableById(new short[]{1});

            double txPower = txPowerSlider.getValue();
            double rxSensitivity = rxSensitivitySlider.getValue();
            antennas.getAntenna(1).setTxPowerinDbm(txPower);
            antennas.getAntenna(1).setRxSensitivityinDbm(rxSensitivity);

            appendOutput(outputArea, "Transmit Power: " + txPower + " dBm, Receive Sensitivity: " + rxSensitivity + " dBm");

            reader.applySettings(settings);
            appendOutput(outputArea, "Settings applied for " + hostname + ".");

            reader.setTagReportListener((r, report) -> {
                for (Tag tag : report.getTags()) {
                    String epc = tag.getEpc().toString();
                    String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                    String time = new SimpleDateFormat("HH:mm:ss").format(new Date());

                    if (!filterCheckbox.isSelected() || epc.length() <= 9) {
                        appendOutput(outputArea, String.format("%-15s %-10s %s", epc, date, time));
                        sendDataToFirestore(epc, hostname, date, time); // Send data to Firestore
                    }
                }
            });

            reader.start();
            appendOutput(outputArea, "Reading started on " + hostname + ". Press Stop Reading to stop.");

        } catch (OctaneSdkException | InterruptedException e) {
            e.printStackTrace();
            appendOutput(outputArea, "Error with " + hostname + ": " + e.getMessage());
        }
    }

    private void stopReading() {
        if (reader1 != null) {
            try {
                reader1.stop();
                reader1.disconnect();
                connectionIndicator1.setBackground(Color.RED);
                appendOutput(outputArea1, "Disconnected from Reader 1.");
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
        if (reader2 != null) {
            try {
                reader2.stop();
                reader2.disconnect();
                connectionIndicator2.setBackground(Color.RED);
                appendOutput(outputArea2, "Disconnected from Reader 2.");
            } catch (OctaneSdkException e) {
                e.printStackTrace();
            }
        }
    }

    private void appendOutput(JTextArea outputArea, String message) {
        outputArea.append(message + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private void clearOutput() {
        outputArea1.setText("");
        outputArea2.setText("");
        appendOutput(outputArea1, String.format("%-15s %-10s %s", "EPC", "Date", "Time"));
        appendOutput(outputArea2, String.format("%-15s %-10s %s", "EPC", "Date", "Time"));
    }

    private void sendDataToFirestore(String epc, String readerName, String date, String time) {
        Firestore db = FirebaseInitializer.getFirestore();

        // Define the path: "RFIDReaders/{readerName}/{epc}"
        DocumentReference tagDocRef = db.collection("RFIDReaders")
                .document(readerName)
                .collection(epc)
                .document("details"); // Can name the document to avoid conflicts

        // Data to store in each tag document
        Map<String, Object> tagData = new HashMap<>();
        tagData.put("time", time);  // Storing time
        tagData.put("date", date);  // Storing date

        try {
            // Set the tag document with the time field
            ApiFuture<WriteResult> future = tagDocRef.set(tagData);

            // Wait for the operation to complete and get the result
            WriteResult result = future.get();
            System.out.println("Tag data for " + epc + " added to reader: " + readerName + " at " + result.getUpdateTime());
        } catch (Exception e) {
            System.err.println("Error adding tag data: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RFIDReaderGUI::new);
    }
}

# RFID Reader GUI 📡

## Overview
This project is a graphical user interface (GUI) application for managing RFID readers and storing tag data in Firebase Firestore. The application connects to two Impinj RFID readers, displays the scanned tags in real-time, and provides options to configure settings, start/stop reading, and clear output. It also includes a feature to store race-specific RFID data in Firebase Firestore.

## Features
- Connects to two RFID readers simultaneously.
- Displays RFID tag data (EPC, date, and time) in real-time.
- Adjustable transmit power and receive sensitivity using sliders.
- Optional filter for EPCs exceeding a certain length.
- Stores tag data in Firebase Firestore with race-specific organization.
- GUI built with Swing for easy interaction.
- Periodic data flushing to Firestore.

## Requirements
### Hardware:
- Two Impinj RFID Readers.
- Computer with a network connection to the RFID readers.

### Software:
- Java 11 or later.
- Firebase Firestore setup with appropriate credentials.
- Impinj Octane SDK.
- Google Cloud Firestore Java SDK.

### Dependencies:
- `javax.swing` for the GUI.
- `com.impinj.octane` for RFID reader communication.
- `com.google.cloud.firestore` for Firebase Firestore integration.

## Setup
1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd <repository-directory>
   ```

2. **Set up Firebase**:
   - Add your Firebase project credentials to the application. Ensure the credentials file is accessible and properly configured in `FirebaseInitializer`.

3. **Configure RFID Readers**:
   - Set up your Impinj RFID readers with IP addresses accessible from your network (e.g., `192.168.10.1` and `192.168.10.2`).

4. **Install Required Libraries**:
   - Include the Impinj Octane SDK and Google Cloud Firestore SDK in your project.
   - Ensure all dependencies are resolved in your build tool (e.g., Maven or Gradle).

5. **Run the Application**:
   Compile and run the `RFIDReaderGUI` class:
   ```bash
   gradle clean build
   gradle run
   ```

## How to Use
1. Launch the application.
2. Enter the name of the race in the "Race Name" field.
3. Adjust the transmit power and receive sensitivity sliders as needed.
4. Check the filter box if you want to filter EPCs longer than 9 characters.
5. Click **Start Reading** to connect to the readers and begin reading tags.
6. View tag data in the two output areas for each reader.
7. Click **Stop Reading** to disconnect the readers.
8. Click **Clear Output** to reset the output areas.

## Data Storage
- RFID data is periodically flushed to Firestore under the following structure:
  ```
  RFIDReaders/{raceName}/{readerName}/{epc}
  ```
- Each tag document includes:
  - `date`: The date the tag was scanned.
  - `time`: The time the tag was scanned.
  - `initialized`: A marker to ensure race setup.

## Notes
- Ensure the race name is set before starting the readers to avoid errors in data flushing.
- Connection indicators show the status of the readers (green for connected, red for disconnected).

## Limitations
- Only supports two RFID readers.
- Requires a stable network connection to both readers.
- Firebase Firestore credentials must be configured correctly.

## Troubleshooting
- **Reader not connecting:**
  - Verify the IP address and network connection.
  - Restart the reader and application.

- **Data not appearing in Firestore:**
  - Ensure Firestore credentials are valid.
  - Check network connectivity.
  - Verify the `raceName` field is not empty.

## License
This project is licensed under the MIT License. See the LICENSE file for details.


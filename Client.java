import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Scanner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

public class Client {
	DatagramSocket socket;
	static final int RETRY_LIMIT = 4;	/* 
	 * UTILITY METHODS PROVIDED FOR YOU 
	 * Do NOT edit the following functions:
	 *      exitErr
	 *      checksum
	 *      checkFile
	 *      isCorrupted  
	 *      
	 */

	/* exit unimplemented method */
	public void exitErr(String msg) {
		System.out.println("Error: " + msg);
		System.exit(0);
	}	

	/* calculate the segment checksum by adding the payload */
	public int checksum(String content, Boolean corrupted)
	{
		if (!corrupted)  
		{
			int i; 
			int sum = 0;
			for (i = 0; i < content.length(); i++)
				sum += (int)content.charAt(i);
			return sum;
		}
		return 0;
	}


	/* check if the input file does exist */
	File checkFile(String fileName)
	{
		File file = new File(fileName);
		if(!file.exists()) {
			System.out.println("SENDER: File does not exists"); 
			System.out.println("SENDER: Exit .."); 
			System.exit(0);
		}
		return file;
	}


	/* 
	 * returns true with the given probability 
	 * 
	 * The result can be passed to the checksum function to "corrupt" a 
	 * checksum with the given probability to simulate network errors in 
	 * file transfer 
	 */
	public boolean isCorrupted(float prob)
	{ 
		double randomValue = Math.random();   
		return randomValue <= prob; 
	}



	/*
	 * The main method for the client.
	 * Do NOT change anything in this method.
	 *
	 * Only specify one transfer mode. That is, either nm or wt
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 5) {
			System.err.println("Usage: java Client <host name> <port number> <input file name> <output file name> <nm|wt>");
			System.err.println("host name: is server IP address (e.g. 127.0.0.1) ");
			System.err.println("port number: is a positive number in the range 1025 to 65535");
			System.err.println("input file name: is the file to send");
			System.err.println("output file name: is the name of the output file");
			System.err.println("nm selects normal transfer|wt selects transfer with time out");
			System.exit(1);
		}

		Client client = new Client();
		String hostName = args[0];
		int portNumber = Integer.parseInt(args[1]);
		InetAddress ip = InetAddress.getByName(hostName);
		File file = client.checkFile(args[2]);
		String outputFile =  args[3];
		System.out.println ("----------------------------------------------------");
		System.out.println ("SENDER: File "+ args[2] +" exists  " );
		System.out.println ("----------------------------------------------------");
		System.out.println ("----------------------------------------------------");
		String choice=args[4];
		float loss = 0;
		Scanner sc=new Scanner(System.in);  


		System.out.println ("SENDER: Sending meta data");
		client.sendMetaData(portNumber, ip, file, outputFile); 

		if (choice.equalsIgnoreCase("wt")) {
			System.out.println("Enter the probability of a corrupted checksum (between 0 and 1): ");
			loss = sc.nextFloat();
		} 

		System.out.println("------------------------------------------------------------------");
		System.out.println("------------------------------------------------------------------");
		switch(choice)
		{
		case "nm":
			client.sendFileNormal (portNumber, ip, file);
			break;

		case "wt": 
			client.sendFileWithTimeOut(portNumber, ip, file, loss);
			break; 
		default:
			System.out.println("Error! mode is not recognised");
		} 


		System.out.println("SENDER: File is sent\n");
		sc.close(); 
	}


	/*
	 * THE THREE METHODS THAT YOU HAVE TO IMPLEMENT FOR PART 1 and PART 2
	 * 
	 * Do not change any method signatures 
	 */

	/* TODO: send metadata (file size and file name to create) to the server 
	 * outputFile: is the name of the file that the server will create
	*/
	public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) throws IOException {
		socket = new DatagramSocket();
		MetaData metadata = new MetaData();
		
		metadata.setName(outputFile);
		metadata.setSize((int) file.length());

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
		objectStream.writeObject(metadata);
		
		byte[] data = outputStream.toByteArray();
		DatagramPacket sendMeta = new DatagramPacket(data, data.length, IPAddress, portNumber);

		socket.send(sendMeta);
		socket.close();

		System.out.println("SERVER: meta data is sent (file name, size): ("+ outputFile +", "+ file.length()+")");
	}


	/* TODO: Send the file to the server without corruption*/
	public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) throws IOException {

		int size = 4;
		int sq = 0;
		int totalPackets = 0;
		FileInputStream fileInputStream = new FileInputStream(file);
		String data = "";


		byte[] buffer = new byte[(int) file.length()];
		fileInputStream.read(buffer);

		for (int i = 0; i < (int) file.length(); i += size) {
			int count = 0;
			byte[] tempBuffer = new byte[size];

			for (int j = 0; j < size; j++) {
				try {
					tempBuffer[j] = buffer[i + j];
				} catch (Exception ArrayIndexOutOfBoundsException) {
					/*Ignores the case when the amount of remaining bytes is less than the size of the packet, and continues to send the rest as a smaller packet. */
					count += 1;
				}
			}
			
			if (count != 0) {
				byte[] lastBuffer = new byte[size-count];
				
				for (int k = 0; k < size-count; k++){
					lastBuffer[k] = tempBuffer[k];
				}
				
				data = new String(lastBuffer);
			} else {
				data = new String(tempBuffer);
			}

			socket = new DatagramSocket();
			Segment segment = new Segment();

			segment.setPayLoad(data);
			segment.setSize(data.length());
			segment.setChecksum(checksum(data, false));
			segment.setSq(sq);
			segment.setType(SegmentType.Data);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
			objectStream.writeObject(segment);
			
			byte[] sendData = outputStream.toByteArray();
			DatagramPacket sendSegment = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);

			System.out.println(String.format("SENDER: Sending segment: sq:%d, size:%d, checksum:%d, content:(%s)", segment.getSq(), segment.getSize(), segment.getChecksum(), segment.getPayLoad()));
			totalPackets += 1;
			socket.send(sendSegment);
			sq = (sq + 1) % 2;

			

			System.out.println("SENDER: Waiting for an ACK");
			byte[] ACKBuffer = new byte[256];
			DatagramPacket receiveACK = new DatagramPacket(ACKBuffer, ACKBuffer.length);
			socket.receive(receiveACK);
			
			Segment seg = new Segment();

			byte[] receiveData = receiveACK.getData();
			ByteArrayInputStream in = new ByteArrayInputStream(receiveData);
			ObjectInputStream is = new ObjectInputStream(in);
			try {
				seg = (Segment) is.readObject();  

			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

			int ack = seg.getSq();
			

			System.out.println(String.format("SENDER: ACK sq=%d RECEIVED.", ack));
			System.out.println("----------------------------------------");
			socket.close();
		
		System.out.println(String.format("total segments %d", totalPackets));
		fileInputStream.close();
		}

		

	} 

	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is 
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) throws IOException {
		int size = 4;
		int sq = 0;
		int totalPackets = 0;
		FileInputStream fileInputStream = new FileInputStream(file);
		String data = "";
		int retry = 0;

		byte[] buffer = new byte[(int) file.length()];
		fileInputStream.read(buffer);

		for (int i = 0; i < (int) file.length(); i += size) {
			int count = 0;
			byte[] tempBuffer = new byte[size];

			for (int j = 0; j < size; j++) {
				try {
					tempBuffer[j] = buffer[i + j];
				} catch (Exception ArrayIndexOutOfBoundsException) {
					/*Ignores the case when the amount of remaining bytes is less than the size of the packet, and continues to send the rest as a smaller packet. */
					count += 1;
				}
			}
			
			if (count != 0) {
				byte[] lastBuffer = new byte[size-count];
				
				for (int k = 0; k < size-count; k++){
					lastBuffer[k] = tempBuffer[k];
				}
				
				data = new String(lastBuffer);
			} else {
				data = new String(tempBuffer);
			}

			socket = new DatagramSocket();
			Segment segment = new Segment();

			segment.setPayLoad(data);
			segment.setSize(data.length());
			segment.setChecksum(checksum(data, isCorrupted(loss)));
			segment.setSq(sq);
			segment.setType(SegmentType.Data);

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
			objectStream.writeObject(segment);
			
			byte[] sendData = outputStream.toByteArray();
			DatagramPacket sendSegment = new DatagramPacket(sendData, sendData.length, IPAddress, portNumber);

			System.out.println(String.format("SENDER: Sending segment: sq:%d, size:%d, checksum:%d, content:(%s)", segment.getSq(), segment.getSize(), segment.getChecksum(), segment.getPayLoad()));
			totalPackets += 1;
			socket.send(sendSegment);

			
			socket.setSoTimeout(100);
			System.out.println("SENDER: Waiting for an ACK");
			

			while (true) {
				try {
					byte[] ACKBuffer = new byte[256];
					DatagramPacket receiveACK = new DatagramPacket(ACKBuffer, ACKBuffer.length);
					socket.receive(receiveACK);
					
					Segment seg = new Segment();

					byte[] receiveData = receiveACK.getData();
					ByteArrayInputStream in = new ByteArrayInputStream(receiveData);
					ObjectInputStream is = new ObjectInputStream(in);
					try {
						seg = (Segment) is.readObject();  
						
						int ack = seg.getSq();
					

						System.out.println(String.format("SENDER: ACK sq=%d RECEIVED.", ack));
						System.out.println("----------------------------------------");
						retry = 0;
						sq = (sq + 1) % 2;
						break;

					} catch (ClassNotFoundException e) {
						e.printStackTrace();
					}

				} catch (Exception SocketTimeoutException) {
					System.out.println("SENDER: ACK not received: TIMEOUT. RESENDING");
					System.out.println("----------------------------------------");
					i-= size;
					retry++;

					if(retry > RETRY_LIMIT) {
						System.out.println("SENDER: TIMEOUTS EXCEED LIMIT. TERMINATING.");
						System.exit(0);
					}

					break;
				}
			}
		
		} 
	System.out.println(String.format("total segments %d", totalPackets));
	fileInputStream.close();
	}
}
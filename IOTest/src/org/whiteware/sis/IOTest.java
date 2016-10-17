package org.whiteware.sis;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;


public class IOTest {

	final JFrame frame = new JFrame("IO Test IPC Example");
	private JTextArea textArea;
	/**
	 * Constructs the IOTest app, it assumes it is the master, given it has been created!
	 * @param fileName
	 */
	public IOTest(String fileName) {
		try {
			final JFrame frame = new JFrame("IO Test IPC Example");
			textArea = new JTextArea();
			JScrollPane scrollPane = new JScrollPane(textArea); 
			textArea.setEditable(false);
			textArea.append("File Names Requested\n--\n");
			frame.add(textArea);
			frame.setMinimumSize(new Dimension(640, 480));
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.pack();
			frame.setLocationRelativeTo(null);
			frame.setVisible(true);

			if ( fileName != null )
				handleFileOpen(fileName);

			new FileNameListener().execute();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void handleFileOpen(String fileName) {
		textArea.append(fileName+"\n");
		frame.toFront();
		frame.requestFocus();
	}

	class FileNameListener extends SwingWorker<Integer, String> {

		@Override
		protected Integer doInBackground() throws Exception {
			ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
			int portNum=serverSocket.getLocalPort();
			publish("Port:"+portNum);

			while(!serverSocket.isClosed()) {
				Socket client = serverSocket.accept();

				BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				String message = in.readLine();
				publish("File:"+message);

				in.close();
				client.close();
			}
			return 1;
		}

		@Override
		protected void process(List<String> chunks) {
			try {
				for ( String message :chunks) {
					String[] words = message.split(":");
					switch(words[0]) {
					case "Port":
						int portNum = Integer.parseInt(words[1]);
						System.out.println("Setting port number "+portNum);
						SingleInstanceSupport.setPortNumber(portNum);
						break;
					case "File":
						handleFileOpen(words[1]);
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Launches the program if the master instance otherwise pass the arguments via the socket to the master
	 * To allow of master instance startup time, a short timer based back-off delay is used when sending the filename
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		if ( SingleInstanceSupport.isMasterInstance() ) {
			System.out.println("We are the master");
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					new IOTest(args.length > 0 ? args[0] : null);
				}
			});
		} else {
			System.out.println("We are not the master");
			int port = 0;
			int sleepTime = 500;
			while ( port == 0 && sleepTime < 5000) {
				try {
					port = SingleInstanceSupport.getMasterPort();
				} catch (Exception e) {
					System.out.println("Sleeping "+sleepTime+"; "+e);
					Thread.sleep(sleepTime *= 2);
				}
			}
			Socket cc = new Socket(InetAddress.getLoopbackAddress(), port);
			PrintWriter out = new PrintWriter(cc.getOutputStream(), true);
			out.println(args.length > 0 ? args[0] : "Dummy Message");
			cc.close();
		}


	}


}

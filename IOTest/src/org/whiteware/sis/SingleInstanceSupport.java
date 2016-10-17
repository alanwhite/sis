package org.whiteware.sis;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;


public class SingleInstanceSupport {
	/**
	 * Explicitly name file offsets for each of the 3 ints stored within
	 */
	private final static int countIntBytes 				= Integer.SIZE / 8;
	private final static int lockIntPosition 			= 0 * countIntBytes;
	private final static int portWrittenIntPosition 	= 1 * countIntBytes;
	private final static int portIntPosition 			= 2 * countIntBytes;

	private final static String LOCKFILE = "IOTestFile.tmp";
	private static File lockFile;
	private static FileChannel fileChannel = null;
	private static FileLock masterLock = null;


	/**
	 * Obtains the port number the master instance is listening on
	 * @return the port number to connect to or 0 if none available
	 */
	public static int getMasterPort() throws Exception {

		String userHome = System.getProperty("user.home");
		if ( userHome == null ) {
			throw(new Exception("no home folder"));
		}

		String lockFilename = userHome + File.separator + LOCKFILE;
		File f = new File(lockFilename);

		FileChannel fileChannel = new RandomAccessFile(f, "r").getChannel();
		ByteBuffer intBuf = ByteBuffer.allocate(3*countIntBytes);
		intBuf.clear();
		
		// we want to read from the unlocked portion of the file
		intBuf.position(portWrittenIntPosition);
		fileChannel.position(portWrittenIntPosition);
		int numRead = fileChannel.read(intBuf);
		
		if ( numRead < countIntBytes ) {
			throw(new Exception("no port data in file yet"));
		}

		int portNumber = 0;
		int portWritten = intBuf.getInt(portWrittenIntPosition);
		if ( portWritten == 1 ) {
			portNumber = intBuf.getInt(portIntPosition);
		} else {
			throw(new Exception("port not yet written to file"));
		}

		return portNumber;
	}

	/**
	 * Checks if we can get an exclusive lock on a well known region of the filename.
	 * Deliberately leaves the lockFile open - not  resource leak as long as you only call this once!
	 * @return true if can own the lock
	 */
	public static boolean isMasterInstance() throws Exception {

		String userHome = System.getProperty("user.home");
		if ( userHome == null ) {
			throw(new Exception("no home folder"));
		}

		lockFile = new File(userHome + File.separator + LOCKFILE);
		fileChannel = new RandomAccessFile(lockFile, "rwd").getChannel();

		// obtain exclusive lock on the first int in the file to become the master
		masterLock = fileChannel.tryLock(lockIntPosition,countIntBytes,false);
		if ( masterLock == null ) {
			fileChannel.close();
			return false;
		}

		lockFile.deleteOnExit();

		ByteBuffer intBuf = ByteBuffer.allocate(3*countIntBytes);
		for ( int i=0; i<3; i++)
			intBuf.putInt(0);
		fileChannel.write(intBuf, 0);

		return true;

	}

	/**
	 * Updates the lock file with the port number to use to communicate with the master instance
	 * @param port
	 */
	public static void setPortNumber(int port) throws Exception {

		if ( fileChannel == null )
			throw(new Exception("lock file not opened"));

		ByteBuffer intBuf = ByteBuffer.allocate(3*countIntBytes);
		intBuf.putInt(portIntPosition, port);
		intBuf.rewind();
		fileChannel.write(intBuf, 0);

		int portWritten = 1;
		intBuf.putInt(portWrittenIntPosition, portWritten);

		intBuf.rewind();
		fileChannel.write(intBuf, 0);
	}
}

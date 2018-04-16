package me.tagavari.airmessage.server;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class Constants {
	static final String APP_NAME = "AirMessage";
	static final String SENTRY_DSN = "https://4240bd1f5e2f4ecfac822f78dda19fce:00d4dcd23b244f46a4489b5b80811d39@sentry.io/301837";
	
	//Creating the version values
	static final String SERVER_VERSION = "0.2";
	static final int SERVER_VERSION_CODE = 1;
	
	//Creating the file values
	static final File applicationSupportDir = new File(System.getProperty("user.home") + '/' + "Library" + '/' + "Application Support" + '/' + "AirMessage");
	static final File uploadDir = new File(applicationSupportDir, "uploads");
	
	//Creating the macOS version values
	static final int[] macOSYosemiteVersion = {10, 10};
	static final int[] macOSHighSierraVersion = {10, 13};
	
	//Creating the regex values
	static final Pattern regExNumerated = Pattern.compile("_\\d+?$");
	static final String reExInteger = "^\\d+$";
	static final String regExSplitFilename = "\\.(?=[^.]+$)";
	
	//Creating the other values
	static final int minPort = 0;
	static final int maxPort = 65535;
	
	static File findFreeFile(File directory, String fileName) {
		//Creating the file
		File file = new File(directory, fileName);
		
		//Checking if the file directory doesn't exist
		if(!directory.exists()) {
			//Creating the directory
			directory.mkdir();
			
			//Returning the file
			return file;
		}
		
		//Looping while the file exists
		while(file.exists()) {
			//Getting the file name and extension
			String[] fileData = file.getName().split(regExSplitFilename);
			String baseFileName = fileData[0];
			String fileExtension = fileData.length > 1 ? fileData[1] : "";
			
			//Checking if the base file name ends with an underscore followed by a number
			if(regExNumerated.matcher(baseFileName).find()) {
				//Creating the starting substring variable
				int numberStartChar = 0;
				
				//Finding the substring start
				for(int i = 0; i < baseFileName.length(); i++)
					if(baseFileName.charAt(i) == '_') numberStartChar = i + 1;
				
				//Getting the number
				int number = Integer.parseInt(baseFileName.substring(numberStartChar)) + 1;
				
				//Substringing the base file name to leave just the name and the underscore
				baseFileName = baseFileName.substring(0, numberStartChar);
				
				//Adding the number to the base file name
				baseFileName += number;
			} else {
				//Adding the number to the base file name
				baseFileName += "_0";
			}
			
			//Adding the extension to the base file name
			baseFileName += "." + fileExtension;
			
			//Setting the new file
			file = new File(directory, baseFileName);
		}
		
		//Returning the file
		return file;
	}
	
	static void recursiveDelete(File file) {
		if(file.isFile()) file.delete();
		else {
			File[] childFiles = file.listFiles();
			if(childFiles != null) for(File childFile : childFiles) recursiveDelete(childFile);
			file.delete();
		}
	}
	
	static int[] getSystemVersion() {
		return parseVersionString(System.getProperty("os.version"));
	}
	
	static int[] parseVersionString(String version) {
		return Stream.of(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
	}
	
	/* Return values
	-1 / version 1 is smaller
	 0 / versions are equal
	 1 / version 1 is greater
	 */
	static int compareVersions(int[] version1, int[] version2) {
		//Iterating over the arrays
		for(int i = 0; i < Math.max(version1.length, version2.length); i++) {
			//Comparing the version values
			int comparison = Integer.compare(i >= version1.length ? 0 : version1[i], i >= version2.length ? 0 : version2[i]);
			
			//Returning if the value is not 0
			if(comparison != 0) return comparison;
		}
		
		//Returning 0 (the loop finished, meaning that there was no difference)
		return 0;
	}
	
	static boolean prepareSupportDir() {
		//Creating the directory if the file doesn't exist
		if(!applicationSupportDir.exists()) {
			boolean result = applicationSupportDir.mkdir();
			if(!result)
				UIHelper.displayAlertDialog("Couldn't start application: failed to create Application Support directory at " + applicationSupportDir.getPath());
			return result;
		}
		
		//Checking if a file at that location exists
		if(applicationSupportDir.isFile()) {
			//Logging the error
			UIHelper.displayAlertDialog("Couldn't start application: File exists at " + applicationSupportDir.getPath() + ". Please remove it to continue.");
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	static File getPrefsFile() {
		//Returning the preferences file path
		return new File(System.getProperty("user.home") + '/' + Constants.class.getPackage().getName());
	}
	
	static class ValueWrapper<T> {
		T value;
		
		ValueWrapper(T value) {
			this.value = value;
		}
	}
	
	public static String getDelimitedString(String[] list, String delimiter) {
		if(list.length == 0) return "";
		else if(list.length == 1) return list[0];
		
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(list[0]);
		for(int i = 1; i < list.length; i++) stringBuilder.append(delimiter).append(list[i]);
		
		return stringBuilder.toString();
	}
	
	/**
	 * Checks to see if a specific port is available.
	 *
	 * @param port the port to check for availability
	 */
	public static boolean checkPortAvailability(int port) {
		//Returning if the port is out of range
		if(port < minPort || port > maxPort) throw new IllegalArgumentException("Invalid start port: " + port);
		
		//Attempting to bind the port
		try (ServerSocket serverSocket = new ServerSocket(port);
			DatagramSocket datagramSocket = new DatagramSocket(port)){
			serverSocket.setReuseAddress(true);
			datagramSocket.setReuseAddress(true);
			
			//Returning true
			return true;
		} catch(IOException exception) {
			//An exception was thrown, port couldn't be bound
		}
		
		//Returning false
		return false;
	}
	
	
	private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	public static String randomAlphaNumericString(int length) {
		StringBuilder builder = new StringBuilder();
		Random random = new Random();
		
		for(int i = 0; i < length; i++) builder.append(ALPHA_NUMERIC_STRING.charAt(random.nextInt(ALPHA_NUMERIC_STRING.length())));
		
		return builder.toString();
	}
	
	static String getMACAddress() {
		try {
			byte[] mac = NetworkInterface.getByInetAddress(InetAddress.getLocalHost()).getHardwareAddress();
			if(mac == null) return null;
			StringBuilder stringBuilder = new StringBuilder();
			for(int i = 0; i < mac.length; i++) stringBuilder.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
			return stringBuilder.toString();
		} catch(UnknownHostException | SocketException exception) {
			exception.printStackTrace();
			return null;
		}
	}
	
	static boolean checkDisconnected(IOException exception) {
		return exception.getMessage().toLowerCase().contains("broken pipe");
	}
	
	static boolean checkDisconnected(SocketException exception) {
		return exception.getMessage().toLowerCase().contains("socket closed");
	}
}
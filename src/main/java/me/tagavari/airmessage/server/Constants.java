package me.tagavari.airmessage.server;

import java.io.File;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class Constants {
	//Creating the version values
	static final String SERVER_VERSION = "0.1.3";
	static final int SERVER_VERSION_CODE = 4;
	
	static final String resourceDefaultCredentialList = "users.txt";
	
	//Creating the file values
	static final String userList = "users.txt";
	static final File uploadDir = new File("uploads");
	static final File applicationSupportDir = new File(System.getProperty("user.home") + '/' + "Library" + '/' + "Application Support" + '/' + "AirMessage");
	
	//Creating the macOS version values
	static final int[] macOSYosemiteVersion = {10, 10};
	static final int[] macOSHighSierraVersion = {10, 13};
	
	//Creating the regex values
	static final Pattern regExNumerated = Pattern.compile("_\\d+?$");
	static final String reExInteger = "^\\d+$";
	static final String regExSplitFilename = "\\.(?=[^.]+$)";
	
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
		return Stream.of(System.getProperty("os.version").split("\\.")).mapToInt(Integer::parseInt).toArray();
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
			if(!result) UIHelper.displayAlertDialog("Couldn't start application: failed to create Application Support directory at " + applicationSupportDir.getPath());
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
}
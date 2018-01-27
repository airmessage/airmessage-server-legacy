package me.tagavari.airmessage.server;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class PropertiesManager {
	//Creating the constants
	private static final String integerRegex = "^\\d+$";
	
	private static final String propertiesName = "server.properties";
	private static final String propertiesComment = "These are not user properties! These properties are used and managed by the server, so you shouldn't have any reason to modify them.";
	
	//Creating the properties variables
	static final String propertyDBLastCheckTime = "lastDatabaseCheckTime";
	
	static boolean saveProperty(String key, long value) {
		return saveProperty(key, Long.toString(value));
	}
	
	static boolean saveProperty(String key, String value) {
		//Creating the properties
		Properties properties = new Properties();
		properties.setProperty(key, value);
		
		//Saving the properties
		try(FileOutputStream outputStream = new FileOutputStream(propertiesName)) {
			properties.store(outputStream, propertiesComment);
		} catch(IOException exception) {
			//Printing the stack trace
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	static String loadPropertyString(String key) {
		//Creating the properties
		Properties properties = new Properties();
		
		try(FileInputStream inputStream = new FileInputStream(propertiesName)) {
			//Reading the properties
			properties.load(inputStream);
		} catch(IOException exception) {
			//Printing the stack trace
			exception.printStackTrace();
			
			//Returning null
			return null;
		}
		
		//Returning the property
		return properties.getProperty(key);
	}
	
	static long loadPropertyLong(String key, long defaultValue) {
		//Getting the value
		String valueString = loadPropertyString(key);
		if(valueString == null) return defaultValue;
		
		//Parsing the value
		if(valueString.matches(integerRegex)) return Integer.parseInt(valueString);
		else return defaultValue;
	}
	
	/* static boolean loadProperties() {
		//Creating the properties
		Properties properties = new Properties();
		
		try(FileInputStream inputStream = new FileInputStream(propertiesName)) {
			//Reading the properties
			properties.load(inputStream);
		} catch(IOException exception) {
			//Printing the stack trace
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
		//Assigning the properties
		String propertyDBLastCheckTimeString = properties.getProperty(propertyDBLastCheckTimeKey);
		if(propertyDBLastCheckTimeString.matches(integerRegex)) propertyDBLastCheckTimeVal = Integer.parseInt(propertyDBLastCheckTimeString);
		else propertyDBLastCheckTimeVal = -1;
		
		//Returning true
		return true;
	} */
}
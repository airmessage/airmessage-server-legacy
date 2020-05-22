package me.tagavari.airmessageserver.server;

import me.tagavari.airmessageserver.exception.KeychainPermissionException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class PreferencesManager {
	//Creating the reference values
	public static final int accountTypeDirect = 0;
	public static final int accountTypeConnect = 1;
	
	private static final int SCHEMA_VERSION = 1;
	
	public static final int defaultPort = 1359;
	
	private static final String javaPrefRoot = "AirMessage";
	private static final String javaPrefSchemaVer = "SchemaVersion";
	private static final String javaPrefAccountType = "AccountType";
	private static final String javaPrefAccountConfirmed = "AccountConfirmed";
	private static final String javaPrefAutoCheckUpdates = "AutomaticUpdateCheck";
	private static final String javaPrefServerPort = "ServerPort";
	private static final String javaPrefConnectUserID = "ConnectUserID";
	
	private static final Preferences preferencesNode = Preferences.userRoot().node(javaPrefRoot);
	private static final String keychainService = "AirMessage";
	private static final String keychainAccountPassword = "airmessage-password";
	private static final String keychainAccountInstallationID = "airmessage-installation";
	
	//Creating the cached preference values
	private static final AtomicReference<String> prefCachePassword = new AtomicReference<>();
	private static final AtomicReference<String> prefCacheInstallationID = new AtomicReference<>();
	
	public static boolean initializePreferences() {
		//Upgrading the preferences version
		upgradePreferences();
		
		//Loading Keychain preferences
		try {
			prefCachePassword.set(readKeychain(keychainAccountPassword));
			prefCacheInstallationID.set(readKeychain(keychainAccountInstallationID));
		} catch(KeychainPermissionException exception) {
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Prompting the user to grant Keychain access
			UIHelper.displayAlertDialog(Main.resources().getString("message.error.keychain_reject"));
			
			return false;
		}
		
		return true;
	}
	
	public static boolean upgradePreferences() {
		//Checking if there is a legacy XML configuration file
		File fileLegacyConfig = new File(Constants.applicationSupportDir, "prefs.xml");
		if(fileLegacyConfig.exists()) {
			//Migrating the preferences to the newer model
			boolean result = migratePreferencesXMLJava();
			fileLegacyConfig.delete();
			if(!result) return false;
		}
		
		/* //Upgrading the schema
		int schema = getPreferences().getInt(javaPrefSchemaVer, -1);
		if(schema == -1 || schema == SCHEMA_VERSION) return true; //No preferences, nothing to do */
		
		//Successfully upgraded
		return true;
	}
	
	//Migrates preferences from XML storage to Java Preferences
	private static boolean migratePreferencesXMLJava() {
		//Loading the document
		Document document;
		try {
			document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(Constants.applicationSupportDir, "prefs.xml"));
		} catch(ParserConfigurationException | IOException | SAXException exception) {
			//Logging the error
			Main.getLogger().log(Level.SEVERE, "Couldn't create document builder", exception);
			
			//Returning false
			return false;
		}
		
		//Checking the schema version
		int schemaVersion = -1;
		NodeList schemaVerNodeList = document.getElementsByTagName("SchemaVer");
		if(schemaVerNodeList.getLength() != 0) {
			String schemaVerString = schemaVerNodeList.item(0).getTextContent();
			if(schemaVerString.matches(Constants.reExInteger)) {
				schemaVersion = Integer.parseInt(schemaVerString);
			}
		}
		if(schemaVersion == -1) return false;
		
		//Reading the password
		String password = null;
		
		//Read password from users.txt
		if(schemaVersion == 1) {
			File userFile = new File(Constants.applicationSupportDir, "users.txt");
			if(userFile.exists()) {
				String line = null;
				try(BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
					line = reader.readLine();
				} catch(IOException exception) {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				}
				
				//Decoding the line
				if(line != null) {
					try {
						password = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
					} catch(IllegalArgumentException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					}
				}
			}
			
			//Deleting the file
			userFile.delete();
		}
		//Read password from config
		else if(schemaVersion == 2) {
			Node element = document.getElementsByTagName("Password").item(0);
			if(element != null) {
				String value = element.getTextContent();
				try {
					password = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
				} catch(IllegalArgumentException exception) {
					Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
				}
			}
		}
		
		//Reading the update check preference
		boolean autoCheckUpdates = false;
		{
			Node element = document.getElementsByTagName("AutomaticUpdateCheck").item(0);
			if(element != null) {
				String value = element.getTextContent();
				autoCheckUpdates = value.equals("true");
			}
		}
		
		//Reading the port preference
		int port = 1359;
		{
			Node element = document.getElementsByTagName("Port").item(0);
			String value;
			if(element != null && (value = element.getTextContent()).matches("^\\d+$")) {
				port = Integer.parseInt(value);
			}
		}
		
		//Saving the new data
		preferencesNode.putInt("SchemaVersion", 1);
		preferencesNode.putInt("AccountType", 0);
		preferencesNode.putBoolean("AccountConfirmed", true);
		preferencesNode.putBoolean("AutomaticUpdateCheck", autoCheckUpdates);
		preferencesNode.putInt("ServerPort", port);
		
		if(password != null) writeKeychain(keychainAccountPassword, password);
		
		return true;
	}
	
	/*
	Keychain exit codes
	44 - not found
	45 - item already exists
	128 - permission denied
	 */
	
	private static String readKeychain(String account) throws KeychainPermissionException {
		try {
			Process process = Runtime.getRuntime().exec(new String[]{
					"security", "find-generic-password",
					"-s", keychainService,
					"-a", account,
					"-w"
			});
			
			if(process.waitFor() == 0) {
				//Reading and returning the input
				BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
				return in.lines().collect(Collectors.joining());
			} else {
				if(process.exitValue() == 128) throw new KeychainPermissionException();
				
				//Logging the error
				try(BufferedReader in = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
					String errorOutput = in.lines().collect(Collectors.joining());
					Main.getLogger().log(Level.WARNING, "Unable to read keychain value (service: " + keychainService + ", account: " + account + "): " + errorOutput);
				}
			}
		} catch(IOException | InterruptedException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
		}
		
		return null;
	}
	
	private static boolean writeKeychain(String account, String password) {
		try {
			Process process = Runtime.getRuntime().exec(new String[]{
					"security", "add-generic-password",
					"-s", keychainService,
					"-a", account,
					"-w", password,
					"-U"
			});
			
			if(process.waitFor() == 0) {
				return true;
			} else {
				//Logging the error
				try(BufferedReader in = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
					String errorOutput = in.lines().collect(Collectors.joining());
					Main.getLogger().log(Level.WARNING, "Unable to write password: " + errorOutput);
				}
			}
		} catch(IOException | InterruptedException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
		}
		
		return false;
	}
	
	public static int getPrefAccountType() {
		return preferencesNode.getInt(javaPrefAccountType, -1);
	}
	
	public static void setPrefAccountType(int accountType) {
		preferencesNode.putInt(javaPrefAccountType, accountType);
	}
	
	public static boolean getPrefAccountConfirmed() {
		return preferencesNode.getBoolean(javaPrefAccountConfirmed, false);
	}
	
	public static void setPrefAccountConfirmed(boolean accountConfirmed) {
		preferencesNode.putBoolean(javaPrefAccountConfirmed, accountConfirmed);
	}
	
	public static boolean getPrefAutoCheckUpdates() {
		return preferencesNode.getBoolean(javaPrefAutoCheckUpdates, true);
	}
	
	public static void setPrefAutoCheckUpdates(boolean autoCheckUpdates) {
		preferencesNode.putBoolean(javaPrefAutoCheckUpdates, autoCheckUpdates);
	}
	
	public static int getPrefServerPort() {
		return preferencesNode.getInt(javaPrefServerPort, defaultPort);
	}
	
	public static void setPrefServerPort(int port) {
		preferencesNode.putInt(javaPrefServerPort, port);
	}
	
	public static String getPrefConnectUserID() {
		return preferencesNode.get(javaPrefConnectUserID, null);
	}
	
	public static void setPrefConnectUserID(String userID) {
		preferencesNode.put(javaPrefConnectUserID, userID);
	}
	
	public static String getPrefPassword() {
		return prefCachePassword.get();
	}
	
	public static boolean setPrefPassword(String password) {
		boolean result = writeKeychain(keychainAccountPassword, password);
		if(result) prefCachePassword.set(password);
		return result;
	}
	
	private static String getPrefInstallationID() {
		return prefCacheInstallationID.get();
	}
	
	private static boolean setPrefInstallationID(String installationID) {
		boolean result = writeKeychain(keychainAccountInstallationID, installationID);
		if(result) prefCacheInstallationID.set(installationID);
		return result;
	}
	
	public static String getInstallationID() {
		//Reading and returning the UUID
		String installationID = getPrefInstallationID();
		if(installationID != null) return installationID;
		
		//Generating, saving and returning a new UUID if an existing one doesn't exist
		installationID = UUID.randomUUID().toString();
		setPrefInstallationID(installationID);
		return installationID;
	}
}
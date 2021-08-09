package me.tagavari.airmessageserver.server;

import io.sentry.Sentry;
import io.sentry.protocol.User;
import me.tagavari.airmessageserver.connection.ConnectionManager;
import me.tagavari.airmessageserver.helper.StringHelper;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.*;

public class Main {
	//Creating the constants
	private static final SecureRandom secureRandom = new SecureRandom();
	private static final File logFile = new File(Constants.applicationSupportDir, "logs/latest.log");
	public static final int databaseScanFrequency = 2 * 1000;
	
	//Creating the variables
	private static boolean debugMode = false;
	private static TimeHelper timeHelper;
	private static Logger logger;
	private static String deviceName;
	private static boolean isAppleSilicon;
	
	private static boolean isSetupMode;
	private static ServerState serverState = ServerState.SETUP;
	
	public static void main(String[] args) throws IOException {
		//Processing the arguments
		processArgs(args);
		
		//Preparing the support directory
		if(!Constants.prepareSupportDir()) return;
		
		//Configuring the logger
		logger = Logger.getGlobal();
		logger.setLevel(Level.FINEST);
		if(!logFile.getParentFile().exists()) logFile.getParentFile().mkdir();
		else if(logFile.exists()) Files.move(logFile.toPath(), Constants.findFreeFile(logFile.getParentFile(), new SimpleDateFormat("YYYY-MM-dd").format(new Date()) + ".log", "-", 1).toPath());
		
		for(Handler handler : logger.getParent().getHandlers()) logger.getParent().removeHandler(handler);
		
		{
			FileHandler handler = new FileHandler(logFile.getPath());
			handler.setLevel(Level.FINEST);
			handler.setFormatter(getLoggerFormatter());
			logger.addHandler(handler);
		}
		
		{
			ConsoleHandler handler = new ConsoleHandler();
			handler.setLevel(Level.FINEST);
			handler.setFormatter(getLoggerFormatter());
			logger.addHandler(handler);
		}
		
		//Reading the device name
		deviceName = SystemAccess.readDeviceName();
		boolean isProcessTranslated = SystemAccess.isProcessTranslated();
		String processorArchitecture = SystemAccess.readProcessorArchitecture();
		isAppleSilicon = isProcessTranslated || "arm".equals(processorArchitecture);
		
		if(isDebugMode()) {
			getLogger().log(Level.INFO, "Server running in debug mode");
			Sentry.init("");
		} else {
			//Initializing Sentry
			Sentry.init(options -> {
				options.setEnableExternalConfiguration(true);
				options.setRelease("airmessage-server@" + Constants.SERVER_VERSION);
				options.setTag("system_version", System.getProperty("os.version"));
				options.setTag("system_architecture", processorArchitecture);
				options.setTag("is_rosetta_translated", Boolean.toString(isProcessTranslated));
				options.setBeforeSend((event, hint) -> {
					if(event.isCrashed() && event.getThrowable() != null) {
						//Log errors to disk
						Main.getLogger().log(Level.SEVERE, "Fatal error", event.getThrowable());
					}
					
					return event;
				});
			});
		}
		
		//Logging the startup messages
		getLogger().info("Starting AirMessage Server version " + Constants.SERVER_VERSION);
		
		//Returning if the system is not valid
		if(!runSystemCheck()) return;
		
		//Initializing properties
		PropertiesManager.initializeProperties();
		
		//Preparing the preferences
		{
			boolean result;
			do {
				result = PreferencesManager.initializePreferences();
			} while(!result);
		}
		
		//Marking the user's ID for Sentry
		{
			User user = new User();
			user.setId(PreferencesManager.getInstallationID());
			Sentry.setUser(user);
		}
		
		//Initializing the UI helper
		UIHelper.initialize();
		
		//Starting the update checker
		if(PreferencesManager.getPrefAutoCheckUpdates()) UpdateManager.startUpdateChecker();
		
		boolean setupNeeded = !PreferencesManager.getPrefAccountConfirmed() ||
							  //If the user deleted their password from Keychain, ask them to set it up again
							  (PreferencesManager.getPrefAccountType() == PreferencesManager.accountTypeConnect && StringHelper.isNullOrEmpty(PreferencesManager.getPrefPassword()));
		
		//Setting up first run
		if(setupNeeded) {
			//The permission check will be run when the user closes the window
			UIHelper.openIntroWindow();
			
			//Enabling setup mode
			setSetupMode(true);
		} else {
			runPermissionCheck();
		}
		
		//Setting up the system tray
		SystemTrayManager.setupSystemTray();
		
		//Getting the time system
		timeHelper = TimeHelper.getCorrectTimeSystem();
		getLogger().info("Using time system " + Main.getTimeHelper().toString() + " with current time " + System.currentTimeMillis() + " -> " + Main.getTimeHelper().toDatabaseTime(System.currentTimeMillis()));
		
		//Hiding JOOQ's splash
		System.setProperty("org.jooq.no-logo", "true");
		
		if(setupNeeded) {
			//Starting the server
			startServer();
		}
		
		//Adding a shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			//Stopping the services
			ConnectionManager.stop();
			DatabaseManager.stop();
			UpdateManager.stopUpdateChecker();
			
			//Deleting the uploads directory
			Constants.recursiveDelete(Constants.uploadDir);
		}));
		
		//Starting the event loop
		UIHelper.startEventLoop();
	}
	
	static void startServer() {
		//Disconnecting the server if it's currently running
		ConnectionManager.stop();
		
		//Setting the data proxy
		ConnectionManager.assignDataProxy();
		
		//Updating the server state
		setServerState(ServerState.STARTING);
		SystemTrayManager.updateStatusMessage();
		
		//Loading the credentials
		//result = SecurityManager.loadCredentials();
		//if(!result) System.exit(1);
		
		//Starting the database scanner
		if(DatabaseManager.getInstance() == null) {
			boolean result = DatabaseManager.start(databaseScanFrequency);
			if(!result) {
				//Updating the server state
				setServerState(ServerState.ERROR_DATABASE);
				SystemTrayManager.updateStatusMessage();
				
				//Returning
				return;
			}
		}
		
		//Starting the server manager
		ConnectionManager.start();
		
		//Logging a message
		getLogger().info("Initialization complete");
	}
	
	static void reinitializeServer() {
		//Returning if the database manager isn't running
		if(DatabaseManager.getInstance() == null) return;
		
		//Updating the server state
		setServerState(ServerState.STARTING);
		SystemTrayManager.updateStatusMessage();
		
		//Disconnecting the server if it's currently running
		ConnectionManager.stop();
		
		//Re-assigning the proxy (to realign with preferences updates)
		ConnectionManager.assignDataProxy();
		
		//Starting the server back up again
		ConnectionManager.start();
		
		//Logging a message
		getLogger().info("Restart complete");
	}
	
	private static boolean runSystemCheck() {
		//Setting the default locale
		//JOptionPane.setDefaultLocale(Locale.getDefault()); //TODO check if necessary
		
		//Checking if the system is not macOS
		{
			String systemName = System.getProperty("os.name");
			if(!systemName.toLowerCase().contains("mac")) {
				//Showing a dialog
				JOptionPane.showMessageDialog(null, resources().getString("message.os_incompatible"), null, JOptionPane.PLAIN_MESSAGE);
				
				//Returning false
				return false;
			}
		}
		
		//Checking if the system version is below OS X yosemite
		if(Constants.compareVersions(Constants.getSystemVersion(), Constants.macOSYosemiteVersion) < 1) {
			//Showing an alert dialog
			return UIHelper.displayVersionWarning();
		}
		
		//Returning true
		return true;
	}
	
	public static void runPermissionCheck() {
		//Checking AppleScript automation permissions
		if(!AppleScriptManager.testAutomation()) UIHelper.displayAutomationWarning();
		
		//Checking disk access permissions (full disk access only applies on macOS 10.14 Mojave or above)
		if(Constants.compareVersions(Constants.getSystemVersion(), Constants.macOSMojaveVersion) >= 0) {
			File messagesDir = new File(System.getProperty("user.home"), "Library/Messages/chat.db");
			if(!messagesDir.canRead()) UIHelper.displayDiskAccessWarning();
		}
	}
	
	private static void processArgs(String[] args) {
		//Iterating over the arguments
		for(String argument : args) {
			//Debug
			if("-debug".equals(argument)) debugMode = true;
		}
	}
	
	public static boolean isDebugMode() {
		return debugMode;
	}
	
	private static Formatter getLoggerFormatter() {
		return new Formatter() {
			private final DateFormat dateFormat = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
			
			@Override
			public String format(LogRecord record) {
				String stackTrace = "";
				if(record.getThrown() != null) {
					StringWriter errors = new StringWriter();
					record.getThrown().printStackTrace(new PrintWriter(errors));
					stackTrace = errors.toString();
				}
				return dateFormat.format(record.getMillis()) + ' ' + '[' + record.getLevel().toString() + ']' + ' ' + formatMessage(record) + '\n' + stackTrace;
			}
		};
	}
	
	public static TimeHelper getTimeHelper() {
		return timeHelper;
	}
	
	public static Logger getLogger() {
		return logger;
	}
	
	public static SecureRandom getSecureRandom() {
		return secureRandom;
	}
	
	public static boolean isSetupMode() {
		return isSetupMode;
	}
	
	public static void setSetupMode(boolean isSetupMode) {
		Main.isSetupMode = isSetupMode;
	}
	
	public static ServerState getServerState() {
		return serverState;
	}
	
	public static void setServerState(ServerState value) {
		serverState = value;
	}
	
	public static ResourceBundle resources() {
		return ResourceBundle.getBundle("me.tagavari.airmessageserver.server.Translations");
	}
	
	public static String getDeviceName() {
		return deviceName;
	}
	
	public static boolean isAppleSilicon() {
		return isAppleSilicon;
	}
}

package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.tagavari.airmessage.connection.ConnectionManager;
import me.tagavari.airmessage.connection.DataProxy;
import me.tagavari.airmessage.connection.direct.DataProxyTCP;

import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.*;

public class Main {
	//Creating the reference values
	public static final String PREFIX_DEBUG = "DEBUG LOG: ";
	public static final int serverStateStarting = 0;
	public static final int serverStateRunning = 1;
	public static final int serverStateFailedDatabase = 2;
	public static final int serverStateFailedServerPort = 3;
	public static final int serverStateFailedServerInternal = 4;
	
	private static final File logFile = new File(Constants.applicationSupportDir, "logs/latest.log");
	
	//Creating the variables
	private static boolean debugMode = false;
	private static TimeHelper timeHelper;
	private static Logger logger;
	
	private static int serverState = serverStateStarting;
	
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
		
		if(isDebugMode()) {
			getLogger().log(Level.INFO, "Server running in debug mode");
		} else {
			//Initializing Sentry
			Sentry.init(Constants.SENTRY_DSN + "?release=" + Constants.SERVER_VERSION);
			
			//Marking the user's ID (with their MAC address)
			//String macAddress = Constants.getMACAddress();
			//if(macAddress != null) Sentry.getContext().setUser(new UserBuilder().setId(macAddress).build());
			
			//Recording the system version
			Sentry.getContext().addTag("system_version", System.getProperty("os.version"));
		}
		
		//Returning if the system is not valid
		if(!runSystemCheck()) return;
		
		//Preparing the preferences
		if(!PreferencesManager.loadPreferences()) return;
		
		//Initializing the UI helper
		UIHelper.initialize();
		
		//Opening the intro window
		if(PreferencesManager.checkFirstRun()) UIHelper.openIntroWindow();
		else runPermissionCheck(); //The permission check will be run when the user closes the window
		
		//Setting up the system tray
		SystemTrayManager.setupSystemTray();
		
		//Getting the time system
		timeHelper = TimeHelper.getCorrectTimeSystem();
		getLogger().info("Using time system " + Main.getTimeHelper().toString() + " with current time " + System.currentTimeMillis() + " -> " + Main.getTimeHelper().toDatabaseTime(System.currentTimeMillis()));
		
		//Hiding JOOQ's splash
		System.setProperty("org.jooq.no-logo", "true");
		
		//Logging the startup messages
		getLogger().info("Starting AirMessage server version " + Constants.SERVER_VERSION);
		
		//Setting the data proxy
		ConnectionManager.assignDataProxy();
		
		//Starting the server
		startServer();
		
		//Starting the update checker
		if(PreferencesManager.getPrefAutoCheckUpdates()) UpdateManager.startUpdateChecker();
		
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
		//Updating the server state
		setServerState(serverStateStarting);
		SystemTrayManager.updateStatusMessage();
		
		//Loading the credentials
		//result = SecurityManager.loadCredentials();
		//if(!result) System.exit(1);
		
		//Starting the database scanner
		{
			boolean result = DatabaseManager.start((int) (PreferencesManager.getPrefScanFrequency() * 1000));
			if(!result) {
				//Updating the server state
				setServerState(serverStateFailedDatabase);
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
		//Updating the server state
		setServerState(serverStateStarting);
		SystemTrayManager.updateStatusMessage();
		
		//Returning if the database manager isn't running
		if(DatabaseManager.getInstance() == null) return;
		
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
			private final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
			
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
	
	public static int getServerState() {
		return serverState;
	}
	
	public static void setServerState(int value) {
		serverState = value;
	}
	
	public static ResourceBundle resources() {
		return ResourceBundle.getBundle("me.tagavari.airmessage.server.Translations");
	}
}
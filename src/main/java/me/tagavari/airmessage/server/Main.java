package me.tagavari.airmessage.server;

import com.github.rodionmoiseev.c10n.C10N;
import com.github.rodionmoiseev.c10n.annotations.DefaultC10NAnnotations;

import javax.swing.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.logging.*;

class Main {
	//Creating the variables
	private static TimeHelper timeHelper;
	private static Logger logger;
	
	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		//Configuring the logger
		logger = Logger.getGlobal();
		for(Handler handler : logger.getParent().getHandlers()) logger.getParent().removeHandler(handler);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.FINEST);
		handler.setFormatter(new Formatter() {
			private final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
			
			@Override
			public String format(LogRecord record) {
				return dateFormat.format(record.getMillis()) + ' ' + '[' + record.getLevel().toString() + ']' + ' ' + formatMessage(record) + '\n';
			}
		});
		logger.addHandler(handler);
		
		//Configuring the internationalization engine
		C10N.configure(new DefaultC10NAnnotations());
		
		//Returning if the system is not valid
		if(!runSystemCheck()) return;
		
		//Preparing the support directory
		if(!Constants.prepareSupportDir()) System.exit(1);
		
		//Preparing the preferences
		if(!PreferencesManager.prepare() || !PreferencesManager.loadPreferences()) System.exit(1);
		
		//Opening the intro window
		UIHelper.openIntroWindow();
		
		//Setting up the system tray
		SystemTrayManager.setupSystemTray();
		
		//Processing the arguments
		processArgs(args);
		
		//Hiding JOOQ's splash
		//System.getProperties().setProperty("org.jooq.no-logo", "true");
		
		//Logging the startup messages
		//getLogger().info("Thank you for using jOOQ " + org.jooq.Constants.FULL_VERSION);
		//getLogger().info("Starting AirMessage server version " + Constants.SERVER_VERSION);
		
		/* //Getting the time system
		timeHelper = TimeHelper.getCorrectTimeSystem();
		
		//Creating the result variable
		boolean result;
		
		//Loading the credentials
		result = SecurityManager.loadCredentials();
		if(!result) System.exit(1);
		
		//Starting the WebSockets manager
		WSServerManager.startServer();
		
		//Starting the database scanner
		result = DatabaseManager.start();
		if(!result) System.exit(1);
		
		//Adding a shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			//Stopping the services
			WSServerManager.stopServer();
			DatabaseManager.stop();
			
			//Deleting the uploads directory
			Constants.recursiveDelete(Constants.uploadDir);
		}));
		
		getLogger().info("Server started, press CTRL + C to quit"); */
		
		//Starting the event loop
		UIHelper.startEventLoop();
	}
	
	private static boolean runSystemCheck() {
		//Setting the default locale
		//JOptionPane.setDefaultLocale(Locale.getDefault()); //TODO check if necessary
		
		//Checking if the system is not macOS
		{
			String systemName = System.getProperty("os.name");
			if(!systemName.toLowerCase().contains("mac")) {
				//Showing a dialog
				JOptionPane.showMessageDialog(null, I18N.i.warning_osIncompatible(systemName), null, JOptionPane.PLAIN_MESSAGE);
				
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
	
	private static void processArgs(String[] args) {
		//Iterating over the arguments
		for(String argument : args) {
			//Debug
			if(argument.equals("-debug")) getLogger().setLevel(Level.FINEST);
		}
	}
	
	static TimeHelper getTimeHelper() {
		return timeHelper;
	}
	
	static Logger getLogger() {
		return logger;
	}
}
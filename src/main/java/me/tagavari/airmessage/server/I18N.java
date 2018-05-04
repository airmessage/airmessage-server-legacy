package me.tagavari.airmessage.server;

import com.github.rodionmoiseev.c10n.C10N;
import com.github.rodionmoiseev.c10n.annotations.En;

public interface I18N {
	I18N i = C10N.get(I18N.class);
	
	@En("Quit AirMessage")
	String button_quitAirMessage();
	
	@En("Open Preferences\u2026")
	String button_openPrefs();
	
	@En("OK")
	String button_ok();
	
	@En("Cancel")
	String button_cancel();
	
	@En("Edit Passwords\u2026")
	String button_editPasswords();
	
	@En("Retry")
	String button_retry();
	
	@En("Install Update")
	String button_installUpdate();
	
	@En("Remind Me Later")
	String button_installUpdate_later();
	
	@En("AirMessage will not run on {0}")
	String warning_osIncompatible(String name);
	
	@En("AirMessage Server is not supported on versions of macOS below 10.10 (Yosemite). Would you like to continue?")
	String warning_osUnsupported();
	
	@En("Server starting...")
	String menu_starting();
	
	@En("Server running")
	String menu_running();
	
	@En("Failed to start server; port is unavailable")
	String menu_err_server_port();
	
	@En("Failed to start server; an internal error occurred")
	String menu_err_server_internal();
	
	@En("Failed to connect to database")
	String menu_err_database();
	
	@En("{0} client(s) connected")
	String menu_clientsConnected(int count);
	
	//@En("Send feedback\u2026")
	//String menu_sendFeedback();
	
	@En("Preferences\u2026")
	String menu_preferences();
	
	@En("Check for Updates\u2026")
	String menu_checkForUpdates();
	
	@En("Welcome to AirMessage Server!")
	String intro_title();
	
	@En("AirMessage Server lives in your menu bar. Click the icon to check the server status and configuration.\nBefore connecting the app, changing the server password is strongly recommended.")
	String intro_text();
	
	@En("Preferences")
	String title_preferences();
	
	@En("Software Update")
	String title_update();
	
	@En("Updating AirMessage")
	String title_updateCheck();
	
	@En("Server port:")
	String pref_port();
	
	@En("Security:")
	String pref_security();
	
	@En("Passwords (one per line):")
	String pref_passwords();
	
	@En("Updates:")
	String pref_updates();
	
	@En("Check for updates automatically")
	String pref_updates_auto();
	
	@En("Scanning:")
	String pref_scanning();
	
	@En("How often to check for new messages (seconds)")
	String pref_scanning_desc();
	
	/* @En("Please enter a server port")
	String warning_noPort();
	
	@En("{0} is outside of the valid port range (1 to 65535)")
	String warning_portRange(int port); */
	
	@En("Checking for updates\u2026")
	String message_checkingUpdates();
	
	@En("You’re up-to-date!")
	String message_upToDate();
	
	@En("AirMessage {0} is currently the newest version available.")
	String message_upToDate_desc(String version);
	
	@En("A new version of {0} is available!")
	String message_updateAvailable_title(String name);
	
	@En("{0} {1} is now available - you have {2}. Would you like to download it now?")
	String message_updateAvailable_description(String name, String currentVer, String newVer);
	
	@En("Release Notes:")
	String message_releaseNotes();
}
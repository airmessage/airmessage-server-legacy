package me.tagavari.airmessage.server;

import com.github.rodionmoiseev.c10n.C10N;
import com.github.rodionmoiseev.c10n.annotations.En;

public interface I18N {
	I18N i = C10N.get(I18N.class);
	
	@En("AirMessage will not run on {0}")
	String warning_osIncompatible(String name);
	
	@En("AirMessage Server is not supported on versions of macOS below 10.10 (Yosemite). Would you like to continue?")
	String warning_osUnsupported();
	
	@En("Server starting...")
	String menu_starting();
	
	@En("Server running")
	String menu_running();
	
	@En("{0} clients connected")
	String menu_clientsConnected(int count);
	
	//@En("Send feedback\u2026")
	//String menu_sendFeedback();
	
	@En("Preferences\u2026")
	String menu_preferences();
	
	@En("Quit AirMessage")
	String menu_quit();
	
	@En("Welcome to AirMessage Server!")
	String intro_title();
	
	@En("AirMessage Server lives in your menu bar. Click the icon to check the server's status and configuration.\nBefore connecting the app, changing the server password is strongly recommended.")
	String intro_text();
	
	@En("Preferences")
	String title_preferences();
	
	@En("Open Preferences\u2026")
	String button_openprefs();
	
	@En("Server port:")
	String pref_port();
	
	@En("Security:")
	String pref_security();
	
	@En("Edit Passwords\u2026")
	String button_editPasswords();
	
	@En("Please enter a server port")
	String warning_noPort();
	
	@En("{0} is outside of the valid port range (1 to 65535)")
	String warning_portRange(int port);
}
package me.tagavari.airmessageserver.server;

import me.tagavari.airmessageserver.connection.ConnectionManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;

public class SystemTrayManager {
	//Creating the images
	private static final Image iconDark;
	private static final Image iconLight;
	
	//Creating the menu items
	private static Shell shell;
	//private static TrayIcon trayIcon;
	private static MenuItem miStatus;
	private static MenuItem miStatusSub;
	private static MenuItem miPrefs;
	
	static {
		//Creating the shell
		shell = new Shell(UIHelper.getDisplay());
		
		//Image.cocoa_new()
		//iconDark = Image.cocoa_new(shell.getDisplay(), SWT.ICON, NSImage.imageNamed(NSString.stringWith("statusicon_black.png")));
		iconDark = new Image(shell.getDisplay(), Main.class.getClassLoader().getResourceAsStream("statusicon_black.png"));
		iconLight = new Image(shell.getDisplay(), Main.class.getClassLoader().getResourceAsStream("statusicon_white.png"));
	}
	
	public static boolean setupSystemTray() {
		//Creating the menu
		Menu menu = new Menu(shell, SWT.POP_UP);
		
		//Server status
		miStatus = new MenuItem(menu, SWT.PUSH);
		miStatus.setText(Main.resources().getString("message.status.waiting"));
		miStatus.setEnabled(false);
		
		//Clients connected
		miStatusSub = new MenuItem(menu, SWT.PUSH);
		miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), 0));
		miStatusSub.setEnabled(false);
		miStatusSub.addListener(SWT.Selection, event -> Main.startServer());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Preferences
		miPrefs = new MenuItem(menu, SWT.PUSH);
		miPrefs.setEnabled(false);
		miPrefs.setText(Main.resources().getString("action.preferences"));
		miPrefs.addListener(SWT.Selection, event -> PreferencesUI.openWindow());
		
		//Check for updates
		MenuItem miUpdate = new MenuItem(menu, SWT.PUSH);
		miUpdate.setText(Main.resources().getString("action.check_update"));
		miUpdate.addListener(SWT.Selection, event -> UpdateManager.requestManualUpdateCheck());
		
		//Divider
		new MenuItem(menu, SWT.SEPARATOR);
		
		//Quit
		MenuItem miQuit = new MenuItem(menu, SWT.PUSH);
		miQuit.setText(Main.resources().getString("action.quit_app"));
		miQuit.addListener(SWT.Selection, event -> System.exit(0));
		
		//Creating the tray item
		TrayItem trayItem = new TrayItem(shell.getDisplay().getSystemTray(), SWT.NONE);
		trayItem.addListener(SWT.MenuDetect, event -> {
			//Updating the images
			trayItem.setImage(getTrayIcon());
			trayItem.setHighlightImage(getHighlightTrayIcon());
			
			//Opening the menu
			menu.setVisible(true);
		});
		trayItem.setImage(getTrayIcon());
		trayItem.setHighlightImage(getHighlightTrayIcon());
		
		/*while (!shell.isDisposed()) {
			if (!shell.getDisplay().readAndDispatch())
				shell.getDisplay().sleep();
		}
		shell.dispose();
		iconLight.dispose();
		iconDark.dispose();*/
		/* trayIcon = new TrayIcon(getTrayIcon());
		trayIcon.setPopupMenu(popupMenu);
		
		try {
			SystemTray.getSystemTray().add(trayIcon);
		} catch(AWTException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
			
			//Returning false
			return false;
		}
		
		//Adding the update listener
		trayIcon.addMouseListener(new MouseListener() {
			@Override
			public void mouseClicked(MouseEvent e) {}
			
			@Override
			public void mousePressed(MouseEvent e) {
				//Updating the icon
				trayIcon.setImage(getTrayIcon());
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {}
			
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseExited(MouseEvent e) {}
		}); */
		
		//Returning true
		return true;
	}
	
	public static void updateStatusMessage() {
		//Getting the state
		ServerState state = Main.getServerState();
		
		//Setting the status text
		miStatus.setText(Main.resources().getString(state.messageID));
		
		//Setting the description text
		if(state.type == ServerState.Constants.typeStatus) {
			if(state == ServerState.RUNNING) {
				miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), ConnectionManager.getConnectionCount()));
			} else {
				miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), 0));
			}
			miStatusSub.setEnabled(false);
		} else {
			miStatusSub.setText(Main.resources().getString("action.retry"));
			miStatusSub.setEnabled(!Main.isSetupMode());
		}
		
		miPrefs.setEnabled(!Main.isSetupMode());
	}
	
	public static void updateConnectionsMessage() {
		//Returning if the state isn't connected
		if(Main.getServerState() != ServerState.RUNNING) return;
		
		//Updating the message
		miStatusSub.setText(MessageFormat.format(Main.resources().getString("message.status.connected_count"), ConnectionManager.getConnectionCount()));
	}
	
	private static Image getTrayIcon() {
		if(checkDarkMode()) return iconLight;
		else return iconDark;
	}
	
	private static Image getHighlightTrayIcon() {
		return iconLight;
	}
	
	private static boolean checkDarkMode() {
		try {
			//Reading the system value
			Process process = Runtime.getRuntime().exec(new String[] {"defaults", "read", "-g", "AppleInterfaceStyle"});
			process.waitFor(100, TimeUnit.MILLISECONDS);
			return process.exitValue() == 0;
		} catch (IOException | InterruptedException | IllegalThreadStateException exception) {
			//Returning false if the value couldn't be read
			return false;
		}
	}
}
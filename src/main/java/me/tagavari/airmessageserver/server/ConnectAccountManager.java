package me.tagavari.airmessageserver.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.tagavari.airmessageserver.connection.ConnectionManager;
import me.tagavari.airmessageserver.connection.DataProxyListener;
import me.tagavari.airmessageserver.connection.connect.DataProxyConnect;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.logging.Level;

public class ConnectAccountManager {
	private static boolean serverRunning = false;
	private static HttpServer server;
	private static int serverPort;
	
	private static Shell windowShell = null;
	
	public static void openAccountWindow(Shell parentShell) {
		//Returning if there is already a shell
		if(windowShell != null) return;
		
		//Starting the server
		startServer();
		
		//Creating the shell
		Shell shell = windowShell = new Shell(parentShell, SWT.SHEET);
		
		//Configuring the layout
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 1;
		gridLayout.marginLeft = UIHelper.windowMargin;
		gridLayout.marginTop = UIHelper.windowMargin;
		gridLayout.marginRight = UIHelper.windowMargin;
		gridLayout.marginBottom = UIHelper.windowMargin;
		gridLayout.verticalSpacing = 5;
		shell.setLayout(gridLayout);
		
		//Creating the UI components
		{
			Browser browser = new Browser(shell, SWT.NONE);
			//browser.setMenu(new Menu(browser));
			browser.setUrl(getServerURL());
			
			new AccountFunctionCallback(browser, "accountFunctionCallback", parentShell);
			new AccountFunctionErrorCallback(browser, "accountFunctionErrorCallback", parentShell);
			
			GridData browserNotesGD = new GridData();
			browserNotesGD.grabExcessHorizontalSpace = browserNotesGD.grabExcessVerticalSpace = true;
			browserNotesGD.horizontalAlignment = browserNotesGD.verticalAlignment = GridData.FILL;
			browserNotesGD.minimumHeight = 100;
			browserNotesGD.heightHint = 100;
			browser.setLayoutData(browserNotesGD);
		}
		
		{
			Button cancelButton = new Button(shell, SWT.PUSH);
			cancelButton.setText(Main.resources().getString("action.cancel"));
			cancelButton.addListener(SWT.Selection, event -> {
				shell.close();
			});
			
			GridData gridData = new GridData();
			gridData.horizontalAlignment = SWT.END;
			cancelButton.setLayoutData(gridData);
		}
		
		//Opening the shell
		shell.addShellListener(new ShellAdapter() {
			@Override
			public void shellClosed(ShellEvent event) {
				windowShell = null;
				stopServer();
			}
		});
		shell.setSize(500, 500);
		shell.open();
	}
	
	private static Shell getConnectingShell(Shell parentShell) {
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.windowMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label title = new Label(shell, SWT.NONE);
		title.setText(Main.resources().getString("progress.check_connection"));
		title.setFont(UIHelper.getFont(title.getFont(), -1, SWT.BOLD));
		GridData titleGD = new GridData();
		titleGD.horizontalAlignment = GridData.FILL;
		titleGD.grabExcessHorizontalSpace = true;
		title.setLayoutData(titleGD);
		
		//Adding the progress bar
		ProgressBar progressBar = new ProgressBar(shell, SWT.HORIZONTAL | SWT.INDETERMINATE);
		GridData progressBarGD = new GridData();
		progressBarGD.horizontalAlignment = GridData.FILL;
		progressBarGD.grabExcessHorizontalSpace = true;
		progressBar.setLayoutData(progressBarGD);
		
		//Packing the shell
		shell.pack();
		
		//Returning the shell
		return shell;
	}
	
	private static Shell getMessageShell(Shell parentShell, String titleText, String bodyText) {
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.sheetMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label labelTitle = new Label(shell, SWT.WRAP);
		labelTitle.setText(titleText);
		labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
		GridData labelTitleGD = new GridData();
		labelTitleGD.grabExcessHorizontalSpace = true;
		labelTitleGD.horizontalAlignment = GridData.FILL;
		labelTitle.setLayoutData(labelTitleGD);
		
		//Adding the description
		Label labelDescription = new Label(shell, SWT.WRAP);
		labelDescription.setText(bodyText);
		GridData labelDescriptionGD = new GridData();
		labelDescriptionGD.grabExcessHorizontalSpace = true;
		labelDescriptionGD.horizontalAlignment = GridData.FILL;
		labelDescription.setLayoutData(labelDescriptionGD);
		
		//Adding the button
		Button closeButton = new Button(shell, SWT.PUSH);
		closeButton.setText(Main.resources().getString("action.ok"));
		GridData closeButtonGD = new GridData();
		closeButtonGD.horizontalAlignment = GridData.END;
		closeButtonGD.widthHint = UIHelper.minButtonWidth;
		closeButton.setLayoutData(closeButtonGD);
		closeButton.addListener(SWT.Selection, event -> shell.close());
		shell.setDefaultButton(closeButton);
		
		//Packing the shell
		shell.pack();
		if(shell.getSize().x > 500) shell.setSize(500, shell.getSize().y);
		
		//Returning the shell
		return shell;
	}
	
	private static Shell getMessageShellDual(Shell parentShell, String titleText, String bodyText, String buttonMainText, Runnable buttonMainCallback, String buttonSecondaryText, Runnable buttonSecondaryCallback) {
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		shell.setMinimumSize(300, 0);
		
		GridLayout shellGL = new GridLayout();
		shellGL.numColumns = 1;
		shellGL.marginTop = shellGL.marginBottom = shellGL.marginLeft = shellGL.marginRight = UIHelper.sheetMargin;
		shellGL.verticalSpacing = UIHelper.windowMargin;
		shell.setLayout(shellGL);
		
		//Adding the title
		Label labelTitle = new Label(shell, SWT.WRAP);
		labelTitle.setText(titleText);
		labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
		GridData labelTitleGD = new GridData();
		labelTitleGD.grabExcessHorizontalSpace = true;
		labelTitleGD.horizontalAlignment = GridData.FILL;
		labelTitle.setLayoutData(labelTitleGD);
		
		//Adding the description
		Label labelDescription = new Label(shell, SWT.WRAP);
		labelDescription.setText(bodyText);
		GridData labelDescriptionGD = new GridData();
		labelDescriptionGD.grabExcessHorizontalSpace = true;
		labelDescriptionGD.horizontalAlignment = GridData.FILL;
		labelDescription.setLayoutData(labelDescriptionGD);
		
		//Configuring the buttons
		{
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			buttonContainer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			
			FormLayout buttonContainerFL = new FormLayout();
			buttonContainer.setLayout(buttonContainerFL);
			
			Button mainButton = new Button(buttonContainer, SWT.PUSH);
			mainButton.setText(buttonMainText);
			FormData acceptButtonFD = new FormData();
			if(mainButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -mainButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			mainButton.setLayoutData(acceptButtonFD);
			mainButton.addListener(SWT.Selection, event -> {
				shell.close();
				if(buttonMainCallback != null) buttonMainCallback.run();
			});
			shell.setDefaultButton(mainButton);
			
			Button secondaryButton = new Button(buttonContainer, SWT.PUSH);
			secondaryButton.setText(buttonSecondaryText);
			FormData discardButtonFD = new FormData();
			if(secondaryButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) discardButtonFD.width = UIHelper.minButtonWidth;
			discardButtonFD.right = new FormAttachment(mainButton);
			discardButtonFD.top = new FormAttachment(mainButton, 0, SWT.CENTER);
			secondaryButton.setLayoutData(discardButtonFD);
			secondaryButton.addListener(SWT.Selection, event -> {
				shell.close();
				if(buttonSecondaryCallback != null) buttonSecondaryCallback.run();
			});
		}
		
		//Packing the shell
		shell.pack();
		if(shell.getSize().x > 500) shell.setSize(500, shell.getSize().y);
		
		//Returning the shell
		return shell;
	}
	
	private static boolean startServer() {
		//Returning if the server is already running
		if(serverRunning) return true;
		
		try {
			server = HttpServer.create(new InetSocketAddress(0), 0);
			
		} catch(IOException exception) {
			exception.printStackTrace();
			return false;
		}
		
		//Setting the context
		server.createContext("/", ConnectAccountManager::handleServer);
		
		//Starting the server
		server.start();
		
		//Recording the running port
		serverPort = server.getAddress().getPort();
		
		//Setting the server as running
		serverRunning = true;
		
		//Logging the event
		Main.getLogger().log(Level.INFO, "Started Connect account server on port " + serverPort);
		
		return true;
	}
	
	private static void stopServer() {
		//Returning if the server is not running
		if(!serverRunning) return;
		
		//Stopping the server
		server.stop(0);
		
		//Setting the server as not running
		serverRunning = false;
		
		//Logging the event
		Main.getLogger().log(Level.INFO, "Stopped Connect account server");
	}
	
	private static String getServerURL() {
		return "http://localhost:" + serverPort;
	}
	
	private static void handleServer(HttpExchange exchange) {
		//Serve web content
		if("GET".equals(exchange.getRequestMethod())) {
			try {
				//Just send file contents to the client
				String path = exchange.getRequestURI().toString();
				String resource = path.equals("/") ? "/index.html" : path;
				InputStream inputStream = ConnectAccountManager.class.getClassLoader().getResourceAsStream("connectsite" + resource);
				if(inputStream == null) {
					//404 not found
					exchange.sendResponseHeaders(404, -1);
				} else {
					//200 OK
					exchange.sendResponseHeaders(200, 0);
					
					try(inputStream; OutputStream outputStream = exchange.getResponseBody()) {
						inputStream.transferTo(outputStream);
					}
				}
			} catch(IOException exception) {
				exception.printStackTrace();
				
				try {
					//500 internal server error
					exchange.sendResponseHeaders(500, -1);
				} catch(IOException exception2) {
					exception2.printStackTrace();
				}
			}
		}
		/* //Process Firebase Authentication response
		else if("POST".equals(exchange.getRequestMethod())) {
			//Reading the response
			String response = null;
			try(BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
				StringBuilder stringBuilder = new StringBuilder();
				String str;
				while((str = reader.readLine()) != null) {
					stringBuilder.append(str);
				}
				
				response = stringBuilder.toString();
			} catch(IOException exception) {
				exception.printStackTrace();
				
				try {
					//500 internal server error
					exchange.sendResponseHeaders(400, -1);
				} catch(IOException exception2) {
					exception2.printStackTrace();
				}
			}
			
			try {
				if(response == null || response.isBlank()) {
					//400 bad request
					exchange.sendResponseHeaders(400, -1);
				} else {
					String[] responseParts = response.split("\n");
					if(responseParts.length != 2) {
						//400 bad request
						exchange.sendResponseHeaders(400, -1);
					} else {
						//200 OK
						exchange.sendResponseHeaders(200, -1);
						
						//Handling the response
						handleAccountResponse(responseParts[0], responseParts[1]);
					}
				}
			} catch(IOException exception) {
				exception.printStackTrace();
				
				try {
					//500 internal server error
					exchange.sendResponseHeaders(400, -1);
				} catch(IOException exception2) {
					exception2.printStackTrace();
				}
			}
		} */
		
		exchange.close();
	}
	
	private static class AccountFunctionCallback extends BrowserFunction {
		private final Shell parentShell;
		public AccountFunctionCallback(Browser browser, String name, Shell parentShell) {
			super(browser, name);
			
			this.parentShell = parentShell;
		}
		
		@Override
		public Object function(Object[] arguments) {
			String idToken = (String) arguments[0];
			String userID = (String) arguments[1];
			handleAccountResponse(idToken, userID, parentShell);
			return null;
		}
	}
	
	private static class AccountFunctionErrorCallback extends BrowserFunction {
		private final Shell parentShell;
		public AccountFunctionErrorCallback(Browser browser, String name, Shell parentShell) {
			super(browser, name);
			
			this.parentShell = parentShell;
		}
		
		@Override
		public Object function(Object[] arguments) {
			String errorName = (String) arguments[0];
			String errorMessage = (String) arguments[1];
			handleAccountError(errorName, errorMessage, parentShell);
			return null;
		}
	}
	
	private static void handleAccountResponse(String idToken, String userID, Shell parentShell) {
		//Closing the shell (which also stops the server)
		if(windowShell != null) windowShell.close();
		
		//Opening the connecting shell
		Shell connectingShell = getConnectingShell(parentShell);
		connectingShell.open();
		
		//Creating the data proxy
		DataProxyConnect dataProxy = new DataProxyConnect(true, idToken);
		dataProxy.addMessageListener(new DataProxyListener() {
			@Override
			public void onStart() {
				UIHelper.getDisplay().asyncExec(() -> {
					//Removing the listener
					dataProxy.removeMessageListener(this);
					
					//Closing the shell
					connectingShell.close();
					
					//Showing a message
					Shell shell = getMessageShell(parentShell, Main.resources().getString("message.connect.title"), Main.resources().getString("message.connect.body"));
					shell.addShellListener(new ShellAdapter() {
						@Override
						public void shellClosed(ShellEvent e) {
							//Closing the parent shell too
							parentShell.close();
							
							//Saving the new initialized state
							PreferencesManager.setPrefAccountType(PreferencesManager.accountTypeConnect);
							PreferencesManager.setPrefConnectUserID(userID);
							PreferencesManager.setPrefAccountConfirmed(true);
							
							//Running the permission check
							Main.runPermissionCheck();
							
							//Disabling setup mode
							Main.setSetupMode(false);
							
							//Starting the database manager
							boolean result = DatabaseManager.start(Main.databaseScanFrequency);
							if(!result) {
								//Updating the server state
								Main.setServerState(ServerState.ERROR_DATABASE);
							}
							
							SystemTrayManager.updateStatusMessage();
						}
					});
					shell.open();
				});
			}
			
			@Override
			public void onStop(ServerState code) {
				UIHelper.getDisplay().asyncExec(() -> {
					//Removing the listener
					dataProxy.removeMessageListener(this);
					
					//Closing the shell
					connectingShell.close();
					
					//Showing a message
					Shell shell;
					if(code.type == ServerState.Constants.typeErrorRecoverable) {
						shell = getMessageShellDual(parentShell, Main.resources().getString("message.connect.error"), Main.resources().getString(code.messageIDLong),
								Main.resources().getString("action.retry"), () -> {
									//Connecting again
									handleAccountResponse(idToken, userID, parentShell);
								}, Main.resources().getString("action.cancel"), null);
					} else {
						shell = getMessageShell(parentShell, Main.resources().getString("message.connect.error"), Main.resources().getString(code.messageIDLong));
					}
					
					shell.open();
				});
			}
			
			@Override
			public void onPause(ServerState code) {
			
			}
			
			@Override
			public void onOpen(Object client) {
			
			}
			
			@Override
			public void onClose(Object client) {
			
			}
			
			@Override
			public void onMessage(Object client, byte[] content, boolean wasEncrypted) {
			
			}
		});
		
		//Attempting a connection
		ConnectionManager.setDataProxy(dataProxy);
		ConnectionManager.start();
	}
	
	private static void handleAccountError(String name, String message, Shell parentShell) {
		//Logging the error
		Main.getLogger().log(Level.WARNING, "Failed to authenticate with Firebase Authentication: " + name + ": " + message);
		
		//Closing the shell (which also stops the server)
		if(windowShell != null) windowShell.close();
		
		//Displaying an error message
		getMessageShell(parentShell, Main.resources().getString("message.signin.error.title"), Main.resources().getString("message.signin.error.body")).open();
	}
}
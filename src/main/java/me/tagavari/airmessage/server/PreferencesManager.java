package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.Base64;
import java.util.logging.Level;

class PreferencesManager {
	//Creating the reference values
	private static final int SCHEMA_VERSION = 2;
	
	private static final File prefFile = new File(Constants.applicationSupportDir, "prefs.xml");
	
	private static final int defaultPort = 1359;
	private static final String defaultPassword = "cookiesandmilk";
	private static final boolean defaultAutoCheckUpdates = true;
	private static final float defaultScanFrequency = 2;
	
	private static final String domTagRoot = "Preferences";
	private static final String domTagSchemaVer = "SchemaVer";
	private static final String domTagFirstRun = "FirstRun";
	private static final String domTagPort = "Port";
	private static final String domTagAutoCheckUpdates = "AutomaticUpdateCheck";
	private static final String domTagScanFrequency = "ScanFrequency";
	private static final String domTagPassword = "Password";
	
	//private static final String encryptionAlgorithm = "AES";
	private static final String textEncoding = "UTF-8";
	
	//Creating the preference values
	private static int prefServerPort = defaultPort;
	private static boolean prefAutoCheckUpdates = defaultAutoCheckUpdates;
	private static float prefScanFrequency = defaultScanFrequency;
	private static String prefPassword = defaultPassword;
	
	//Creating the window values
	private static Text pwTextPort;
	private static Button pwButtonAutoUpdate;
	private static Text pwTextScanFrequency;
	private static Shell windowShell = null;
	
	//Creating the other values
	//private static byte[] deviceSerial = "000000000000".getBytes();
	//private static Cipher cipher;
	
	/* static boolean prepare() {
		try {
			//Setting the cipher
			cipher = Cipher.getInstance(encryptionAlgorithm);
			
			//Returning true
			return true;
		} catch(NoSuchAlgorithmException | NoSuchPaddingException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.SEVERE, "Couldn't get cipher instance", exception);
			
			//Returning false
			return false;
		}
	} */
	
	static boolean loadPreferences() {
		/* //Attempting to get the device's serial number
		try {
			//Running the command to fetch the device's serial number
			Process process = Runtime.getRuntime().exec("ioreg -c IOPlatformExpertDevice -d 2 | awk -F\\\" '/IOPlatformSerialNumber/{print $(NF-1)}'");
			
			//Returning false if there was any error
			try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				boolean errorLines = false;
				String lsString;
				while ((lsString = errorReader.readLine()) != null) {
					Main.getLogger().severe(lsString);
					errorLines = true;
				}
				
				//Fetching the serial number if there are no error lines
				if(!errorLines) deviceSerial = inputReader.readLine().getBytes();
			}
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, "Couldn't read device serial number", exception);
		} */
		
		//Checking if the preference file exists
		if(prefFile.exists()) {
			//Loading the document
			Document document;
			try {
				document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(prefFile);
			} catch(ParserConfigurationException | IOException | SAXException exception) {
				//Logging the error
				Main.getLogger().log(Level.SEVERE, "Couldn't create document builder", exception);
				
				//Returning false
				return false;
			}
			
			//Checking the schema version
			int schemaVersion = -1;
			NodeList schemaVerNodeList = document.getElementsByTagName(domTagSchemaVer);
			if(schemaVerNodeList.getLength() != 0) {
				String schemaVerString = schemaVerNodeList.item(0).getTextContent();
				if(schemaVerString.matches(Constants.reExInteger)) {
					schemaVersion = Integer.parseInt(schemaVerString);
				}
			}
			
			boolean preferencesUpdateRequired = false;
			
			System.out.println("Loaded preferences with schema version " + schemaVersion);
			//Checking if the document's schema version is invalid
			if(schemaVersion == -1 || schemaVersion > SCHEMA_VERSION) {
				//Discarding and re-creating the preferences
				return createPreferences();
			} else if(schemaVersion < SCHEMA_VERSION) {
				//Requesting a preferences update (to save the upgrade data)
				preferencesUpdateRequired = true;
				
				boolean result;
				while(true) {
					//Upgrading the schema
					result = upgradeSchema(document, schemaVersion);
					
					//Checking if the result is a success
					if(result) {
						//Updating the schema value in the preferences
						findCreateElement(document, findCreateRootNode(document), domTagSchemaVer).setTextContent(Integer.toString(SCHEMA_VERSION));
						
						//Breaking from the loop
						break;
					}
					
					//Displaying a schema upgrade failure warning (abort / continue)
					int selection = UIHelper.displaySchemaWarning();
					
					//Returning false if the selection was "abort" (quitting the app)
					if(selection == 0) return false;
					//Discarding and re-creating the preferences if the selection was "ignore"
					else if(selection == 2) return createPreferences();
					//Otherwise, let the loop continue on and try again
				}
			}
			
			//Reading the preferences
			{
				Node element = findElement(document, domTagPort);
				String value;
				if(element != null && (value = element.getTextContent()).matches("^\\d+$")) {
					prefServerPort = Integer.parseInt(value);
				} else {
					prefServerPort = defaultPort;
					preferencesUpdateRequired = true;
				}
			}
			
			{
				Node element = findElement(document, domTagAutoCheckUpdates);
				
				boolean valueValidated = false;
				if(element != null) {
					String value = element.getTextContent();
					if(value.equals("true")) {
						prefAutoCheckUpdates = true;
						valueValidated = true;
					} else if(value.equals("false")) {
						prefAutoCheckUpdates = false;
						valueValidated = true;
					}
				}
				
				if(!valueValidated) {
					prefServerPort = defaultPort;
					preferencesUpdateRequired = true;
				}
			}
			
			{
				Node element = findElement(document, domTagScanFrequency);
				String value;
				if(element != null && (value = element.getTextContent()).matches("^\\d+\\.\\d+$")) {
					prefScanFrequency = Float.parseFloat(value);
				} else {
					prefScanFrequency = defaultScanFrequency;
					preferencesUpdateRequired = true;
				}
			}
			
			{
				Node element = findElement(document, domTagPassword);
				
				boolean valueValidated = false;
				if(element != null) {
					String value = element.getTextContent();
					try {
						prefPassword = new String(Base64.getDecoder().decode(value), textEncoding);
						valueValidated = true;
					} catch(UnsupportedEncodingException exception) {
						Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
						Sentry.capture(exception);
					} catch(IllegalArgumentException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					}
				}
				
				if(!valueValidated) {
					prefScanFrequency = defaultScanFrequency;
					preferencesUpdateRequired = true;
				}
			}
			
			//Updating the preferences if it has been requested
			if(preferencesUpdateRequired) savePreferences();
			
			//Returning true
			return true;
		} else {
			//Creating the preferences
			return createPreferences();
		}
	}
	
	private static boolean upgradeSchema(Document document, int oldVersion) {
		switch(oldVersion) {
			case 1: { //Multiple passwords (insecure) to single prefPassword (used for encryption)
				//Creating the file
				File userFile = new File(Constants.applicationSupportDir, "users.txt");
				
				//Reading the first password
				String encodedPassword;
				if(!userFile.exists()) {
					try {
						encodedPassword = Base64.getEncoder().encodeToString(defaultPassword.getBytes(textEncoding));
					} catch(UnsupportedEncodingException exception) {
						//Logging the exception
						Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
						Sentry.capture(exception);
						
						//Returning false
						return false;
					}
				} else {
					String line = null;
					try(BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
						line = reader.readLine();
					} catch(IOException exception) {
						Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
					}
					
					//Decoding the password
					if(line == null) {
						try {
							encodedPassword = Base64.getEncoder().encodeToString(defaultPassword.getBytes(textEncoding));
						} catch(UnsupportedEncodingException exception) {
							//Logging the exception
							Main.getLogger().log(Level.SEVERE, exception.getMessage(), exception);
							Sentry.capture(exception);
							
							//Returning false
							return false;
						}
					} else {
						encodedPassword = line;
					}
				}
				
				//Writing the password
				findCreateElement(document, findCreateRootNode(document), domTagPassword).setTextContent(encodedPassword);
				
				//Deleting the user file
				userFile.delete();
			}
		}
		
		//Returning true
		return true;
	}
	
	private static boolean createPreferences() {
		//Setting the default values
		prefServerPort = defaultPort;
		
		//Writing the preferences to disk
		return savePreferences();
	}
	
	private static boolean savePreferences() {
		try {
			//Creating the document
			Document document;
			if(prefFile.exists()) {
				try {
					document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(prefFile);
				} catch(ParserConfigurationException | IOException | SAXException exception) {
					//Logging the error
					Main.getLogger().log(Level.SEVERE, "Couldn't create document builder / " + exception.getMessage(), exception);
					
					//Returning false
					return false;
				}
			} else document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			
			//Building the XML structure
			Node rootNode = findCreateRootNode(document);
			findCreateElement(document, rootNode, domTagSchemaVer).setTextContent(Integer.toString(SCHEMA_VERSION));
			findCreateElement(document, rootNode, domTagPort).setTextContent(Integer.toString(prefServerPort));
			findCreateElement(document, rootNode, domTagAutoCheckUpdates).setTextContent(Boolean.toString(prefAutoCheckUpdates));
			findCreateElement(document, rootNode, domTagScanFrequency).setTextContent(Float.toString(prefScanFrequency));
			findCreateElement(document, rootNode, domTagPassword).setTextContent(Base64.getEncoder().encodeToString(prefPassword.getBytes(textEncoding)));
			
			//Writing the XML document
			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(prefFile));
		} catch(ParserConfigurationException | TransformerException | UnsupportedEncodingException exception) {
			//Logging the exception
			Main.getLogger().log(Level.SEVERE, "Couldn't create new XML document: " + exception.getMessage(), exception);
			Sentry.capture(exception);
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	private static Node findCreateRootNode(Document document) {
		return findCreateElement(document, document, domTagRoot);
	}
	
	/**
	 * Finds the specified element in the document
	 * @param document the document to search
	 * @param name the name of the element to find
	 * @return the first instance of specified element, or null if the element couldn't be found
	 */
	private static Node findElement(Document document, String name) {
		NodeList list = document.getElementsByTagName(name);
		if(list.getLength() == 0) return null;
		else return list.item(0);
	}
	
	private static Node findCreateElement(Document document, Node parent, String name) {
		NodeList list = parent.getChildNodes();
		for(int i = 0; i < list.getLength(); i++) {
			Node node = list.item(i);
			if(name.equals(node.getNodeName())) return node;
		}
		Node node = document.createElement(name);
		parent.appendChild(node);
		return node;
	}
	
	private static void applyPreferenceChanges() {
		{
			//Getting the port text
			String portText = pwTextPort.getText();
			if(!portText.isEmpty()) {
				//Updating the port
				prefServerPort = Integer.parseInt(portText); //Input is forced to be numerical due to input filter
			}
		}
		
		{
			//Getting the auto update
			boolean autoCheckUpdatesControl = pwButtonAutoUpdate.getSelection();
			if(prefAutoCheckUpdates != autoCheckUpdatesControl) {
				prefAutoCheckUpdates = autoCheckUpdatesControl;
				
				//Updating the update manager
				if(prefAutoCheckUpdates) UpdateManager.startUpdateChecker();
				else UpdateManager.stopUpdateChecker();
			}
		}
		
		{
			//Getting the port text
			String scanFrequencyText = pwTextScanFrequency.getText();
			if(!scanFrequencyText.isEmpty()) {
				//Updating the port
				prefScanFrequency = Float.parseFloat(scanFrequencyText); //Input is forced to be numerical due to input filter
			}
		}
		
		//Saving the preferences
		savePreferences();
		
		//Updating the database manager
		DatabaseManager databaseManager = DatabaseManager.getInstance();
		if(databaseManager != null) databaseManager.scannerThread.updateScanFrequency((int) (prefScanFrequency * 1000));
		
		//Restarting the server
		Main.restartServer();
	}
	
	static boolean checkFirstRun() {
		//Returning true if the document doesn't exist
		//if(!prefFile.exists()) return true;
		
		//Loading the document
		Document document;
		try {
			document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(prefFile);
		} catch(ParserConfigurationException | IOException | SAXException exception) {
			//Logging the error
			Main.getLogger().log(Level.SEVERE, "Couldn't create document builder", exception);
			
			//Returning false
			return false;
		}
		
		boolean firstRun;
		
		//Getting the first run
		NodeList firstRunNL = document.getElementsByTagName(domTagFirstRun);
		if(firstRunNL.getLength() == 0) firstRun = true;
		else {
			String firstRunString = document.getElementsByTagName(domTagFirstRun).item(0).getTextContent();
			if(firstRunString.equals(Boolean.toString(false))) firstRun = false;
			else if(firstRunString.equals(Boolean.toString(true))) firstRun = true;
			else firstRun = true;
		}
		
		if(firstRun) {
			//Adding the XML element
			Node rootElement = document.getElementsByTagName(domTagRoot).item(0);
			Element element = document.createElement(domTagFirstRun);
			element.setTextContent(Boolean.toString(false));
			rootElement.appendChild(element);
			
			try {
				//Writing the XML document
				TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(prefFile));
			} catch(TransformerException exception) {
				Main.getLogger().log(Level.SEVERE, "Couldn't create document builder / " + exception.getMessage(), exception);
			}
		}
		
		//Returning the value
		return firstRun;
	}
	
	static void openWindow() {
		//Opening the window
		if(windowShell == null || windowShell.isDisposed()) openPrefsWindow();
		else windowShell.forceActive();
	}
	
	private static void openPrefsWindow() {
		//Creating the shell
		windowShell = new Shell(UIHelper.getDisplay(), SWT.TITLE);
		windowShell.setText(I18N.i.title_preferences());
		
		//Configuring the layouts
		GridLayout shellGL = new GridLayout(1, false);
		shellGL.marginLeft = shellGL.marginRight = shellGL.marginTop = shellGL.marginBottom = shellGL.marginWidth = shellGL.marginHeight = 0;
		shellGL.verticalSpacing = 5;
		windowShell.setLayout(shellGL);
		
		//Configuring the preferences
		Composite prefContainer = new Composite(windowShell, SWT.NONE);
		GridLayout prefContainerGL = new GridLayout(2, false);
		prefContainerGL.marginLeft = 100;
		prefContainerGL.marginRight = 50;
		prefContainerGL.marginTop = UIHelper.windowMargin;
		prefContainerGL.marginBottom = UIHelper.windowMargin;
		prefContainerGL.verticalSpacing = 5;
		prefContainer.setLayout(prefContainerGL);
		
		{
			Label portLabel = new Label(prefContainer, SWT.NONE);
			pwTextPort = new Text(prefContainer, SWT.BORDER);
			
			portLabel.setText(I18N.i.pref_port());
			portLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
			
			pwTextPort.setTextLimit(5);
			pwTextPort.addVerifyListener(event -> {
				//Returning if the text is invalid
				if(event.text.isEmpty()) return;
				
				//Trimming the text
				event.text = event.text.trim();
				
				//Iterating over the text's characters and rejecting the event if a non-numerical character was found
				for(char stringChar : event.text.toCharArray()) if(!('0' <= stringChar && stringChar <= '9')) {
					event.doit = false;
					return;
				}
				
				//Assembling the full string
				String originalString = ((Text) event.widget).getText();
				String fullString = originalString.substring(0, event.start) + event.text + originalString.substring(event.end);
				
				//Returning if the string is empty
				if(fullString.isEmpty()) return;
				
				//Rejecting the event if the value is not within the port range
				int portNum = Integer.parseInt(fullString);
				if(portNum < 1 || portNum > 65535) {
					event.doit = false;
					return;
				}
			});
			GridData textGD = new GridData();
			textGD.grabExcessHorizontalSpace = false;
			textGD.grabExcessVerticalSpace = false;
			textGD.widthHint = 43;
			pwTextPort.setLayoutData(textGD);
			pwTextPort.setText(Integer.toString(prefServerPort));
			/* GridData textWarningGD = new GridData();
			textWarningGD.widthHint = 250;
			textWarning.setLayoutData(textWarningGD); */
		}
		
		{
			Label securityLabel = new Label(prefContainer, SWT.NONE);
			Button prefsButton = new Button(prefContainer, SWT.PUSH);
			
			securityLabel.setText(I18N.i.pref_security());
			securityLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
			
			prefsButton.setText(I18N.i.button_editPasswords());
			GridData prefGB = new GridData(GridData.BEGINNING, GridData.CENTER, false, false);
			prefGB.horizontalIndent = -8;
			prefsButton.setLayoutData(prefGB);
			prefsButton.addListener(SWT.Selection, event -> openPrefsPasswordWindow(windowShell));
		}
		
		{
			Label updateLabel = new Label(prefContainer, SWT.NONE);
			pwButtonAutoUpdate = new Button(prefContainer, SWT.CHECK);
			
			updateLabel.setText(I18N.i.pref_updates());
			updateLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
			
			pwButtonAutoUpdate.setText(I18N.i.pref_updates_auto());
			GridData prefGB = new GridData(GridData.BEGINNING, GridData.CENTER, false, false);
			pwButtonAutoUpdate.setLayoutData(prefGB);
			pwButtonAutoUpdate.setSelection(prefAutoCheckUpdates);
		}
		
		{
			Label dbScanLabel = new Label(prefContainer, SWT.NONE);
			Composite dbScanComposite = new Composite(prefContainer, SWT.NONE);
			pwTextScanFrequency = new Text(dbScanComposite, SWT.BORDER);
			Label dbScanDesc = new Label(dbScanComposite, SWT.NONE);
			
			dbScanLabel.setText(I18N.i.pref_scanning());
			GridData dbScanLabelGD = new GridData(GridData.END, GridData.BEGINNING, false, false);
			dbScanLabelGD.verticalIndent = 4;
			dbScanLabel.setLayoutData(dbScanLabelGD);
			
			RowLayout compositeLayout = new RowLayout();
			compositeLayout.type = SWT.VERTICAL;
			dbScanComposite.setLayout(compositeLayout);
			
			RowData dbScanTextRD = new RowData();
			dbScanTextRD.width = 43;
			pwTextScanFrequency.setLayoutData(dbScanTextRD);
			pwTextScanFrequency.addVerifyListener(event -> {
				//Returning if the text is invalid
				if(event.text.isEmpty()) return;
				
				//Trimming the text
				event.text = event.text.trim();
				
				//Iterating over the text's characters and rejecting the event if a non-numerical character was found
				for(char stringChar : event.text.toCharArray()) if(!('0' <= stringChar && stringChar <= '9') && stringChar != '.') {
					event.doit = false;
					return;
				}
				
				//Assembling the full string
				String originalString = ((Text) event.widget).getText();
				//String fullString = originalString.substring(0, event.start) + event.text + originalString.substring(event.end);
				
				//Returning if the string is empty
				//if(fullString.isEmpty()) return;
				
				//Rejecting the event if there is already a decimal point in the string
				if(event.text.contains(".") && originalString.contains(".")) {
					event.doit = false;
					return;
				}
			});
			
			pwTextScanFrequency.setText(Float.toString(prefScanFrequency));
			
			dbScanDesc.setText(I18N.i.pref_scanning_desc());
			dbScanDesc.setFont(UIHelper.getFont(dbScanDesc.getFont(), 10, -1));
		}
		
		//Adding the divider
		{
			Label divider = new Label(windowShell, SWT.HORIZONTAL);
			divider.setBackground(UIHelper.getDisplay().getSystemColor(SWT.COLOR_GRAY));
			GridData dividerGD = new GridData(GridData.FILL_HORIZONTAL);
			dividerGD.heightHint = 1;
			divider.setLayoutData(dividerGD);
		}
		
		Composite buttonContainer = new Composite(windowShell, SWT.NONE);
		buttonContainer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		//Configuring the buttons
		{
			FormLayout buttonContainerFL = new FormLayout();
			//buttonContainerFL.marginLeft = buttonContainerFL.marginRight = buttonContainerFL.marginTop = buttonContainerFL.marginBottom = buttonContainerFL.marginWidth = buttonContainerFL.marginHeight = 0;
			buttonContainerFL.marginWidth = buttonContainerFL.marginHeight = UIHelper.dialogButtonBarMargin;
			buttonContainer.setLayout(buttonContainerFL);
			
			Button acceptButton = new Button(buttonContainer, SWT.PUSH);
			acceptButton.setText(I18N.i.button_ok());
			FormData acceptButtonFD = new FormData();
			if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			acceptButton.addListener(SWT.Selection, event -> {
				applyPreferenceChanges();
				windowShell.close();
			});
			windowShell.setDefaultButton(acceptButton);
			
			Button discardButton = new Button(buttonContainer, SWT.PUSH);
			discardButton.setText(I18N.i.button_cancel());
			FormData discardButtonFD = new FormData();
			if(discardButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) discardButtonFD.width = UIHelper.minButtonWidth;
			discardButtonFD.right = new FormAttachment(acceptButton);
			discardButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			discardButton.setLayoutData(discardButtonFD);
			discardButton.addListener(SWT.Selection, event -> windowShell.close());
		}
		
		//Packing the shell
		windowShell.pack();
		windowShell.setMinimumSize(500, windowShell.getMinimumSize().y);
		
		//Centering the window
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = windowShell.getBounds();
		windowShell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Invalidating the reference when the shell is closed
		windowShell.addListener(SWT.Close, event -> windowShell = null);
		
		//Opening the shell
		windowShell.open();
		windowShell.forceActive();
	}
	
	private static void openPrefsPasswordWindow(Shell parentShell) {
		//Creating the shell flags
		Constants.ValueWrapper<Boolean> textEditorOpen = new Constants.ValueWrapper<>(Boolean.FALSE);
		
		//Creating the shell
		Shell shell = new Shell(parentShell, SWT.SHEET);
		
		//Configuring the shell
		shell.addListener(SWT.Traverse, event -> {
			//Cancelling the event if the editor is open and the button is "escape" or "return" (to exit the dialog)
			if(textEditorOpen.value && (event.detail == SWT.TRAVERSE_ESCAPE || event.detail == SWT.TRAVERSE_RETURN)) event.doit = false;
		});
		
		//Configuring the layout
		GridLayout shellGL = new GridLayout(1, false);
		shellGL.marginWidth = shellGL.marginHeight = UIHelper.sheetMargin;
		shell.setLayout(shellGL);
		
		//Creating the relevant widget values
		Text passText;
		
		{
			//Creating the list label
			Label listLabel = new Label(shell, SWT.NONE);
			listLabel.setText(I18N.i.pref_passwords());
			
			//Creating the text
			passText = new Text(shell, SWT.BORDER);
			passText.setText(prefPassword);
			
			GridData passTextGD = new GridData();
			passTextGD.horizontalAlignment = GridData.FILL;
			//passTextGD.verticalAlignment = GridData.FILL;
			//passTextGD.grabExcessVerticalSpace = true;
			passTextGD.grabExcessHorizontalSpace = true;
			passText.setLayoutData(passTextGD);
		}
		
		{
			//Creating the button composite
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			FormLayout buttonCompositeFL = new FormLayout();
			buttonCompositeFL.marginWidth = buttonCompositeFL.marginHeight = 0;
			buttonContainer.setLayout(buttonCompositeFL);
			GridData buttonCompositeGD = new GridData();
			buttonCompositeGD.horizontalAlignment = GridData.FILL;
			buttonCompositeGD.grabExcessHorizontalSpace = true;
			buttonContainer.setLayoutData(buttonCompositeGD);
			
			//Adding the apply / discard buttons
			Button acceptButton = new Button(buttonContainer, SWT.PUSH);
			acceptButton.setText(I18N.i.button_ok());
			FormData acceptButtonFD = new FormData();
			if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.smallMinButtonWidth) acceptButtonFD.width = UIHelper.smallMinButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			passText.addModifyListener(event -> acceptButton.setEnabled(!passText.getText().isEmpty()));
			acceptButton.addListener(SWT.Selection, event -> {
				//Setting the password
				prefPassword = passText.getText();
				
				//Closing the shell
				shell.close();
			});
			shell.setDefaultButton(acceptButton);
			
			Button discardButton = new Button(buttonContainer, SWT.PUSH);
			discardButton.setText(I18N.i.button_cancel());
			FormData discardButtonFD = new FormData();
			if(discardButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.smallMinButtonWidth) discardButtonFD.width = UIHelper.smallMinButtonWidth;
			discardButtonFD.right = new FormAttachment(acceptButton);
			discardButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			discardButton.setLayoutData(discardButtonFD);
			discardButton.addListener(SWT.Selection, event -> shell.close());
		}
		
		//Adding a listener to the shell
		/* shell.addListener(SWT.Close, closeEvent -> {
		
		}); */
		
		//Opening the dialog
		shell.pack();
		shell.setSize(300, shell.getSize().y);
		shell.open();
	}
	
	static int getPrefServerPort() {
		return prefServerPort;
	}
	
	static boolean getPrefAutoCheckUpdates() {
		return prefAutoCheckUpdates;
	}
	
	static float getPrefScanFrequency() {
		return prefScanFrequency;
	}
}
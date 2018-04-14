package me.tagavari.airmessage.server;

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
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.logging.Level;

public class PreferencesManager {
	//Creating the reference values
	private static final int SCHEMA_VERSION = 1;
	
	private static final File prefFile = new File(Constants.applicationSupportDir, "prefs.xml");
	private static final File userFile = new File(Constants.applicationSupportDir, "users.txt");
	
	private static final int defaultPort = 1359;
	private static final String[] defaultPasswords = {"cookiesandmilk"};
	private static final boolean defaultAutoCheckUpdates = true;
	private static final float defaultScanFrequency = 2;
	
	private static final String domTagRoot = "Preferences";
	private static final String domTagSchemaVer = "SchemaVer";
	private static final String domTagFirstRun = "FirstRun";
	private static final String domTagPort = "Port";
	private static final String domTagAutoCheckUpdates = "AutomaticUpdateCheck";
	private static final String domTagScanFrequency = "ScanFrequency";
	
	private static final String encryptionAlgorithm = "AES";
	private static final String textEncoding = "UTF-8";
	
	//Creating the preference values
	private static int serverPort = defaultPort;
	private static boolean autoCheckUpdates = defaultAutoCheckUpdates;
	private static float scanFrequency = defaultScanFrequency;
	
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
			boolean schemaValid = false;
			NodeList schemaVerNodeList = document.getElementsByTagName(domTagSchemaVer);
			if(schemaVerNodeList.getLength() != 0) {
				String schemaVerString = schemaVerNodeList.item(0).getTextContent();
				if(schemaVerString.matches(Constants.reExInteger)) {
					int schemaVersion = Integer.parseInt(schemaVerString);
					
					//TODO compare for older schema version and upgrade XML
					schemaValid = schemaVersion == SCHEMA_VERSION;
				}
			}
			
			//Checking if the document's schema version is invalid
			if(!schemaValid) {
				//Discarding and re-creating the preferences
				return createPreferences();
			}
			
			//Reading the preferences
			boolean preferencesUpdateRequired = false;
			
			String portString = document.getElementsByTagName(domTagPort).item(0).getTextContent();
			if(portString.matches("^\\d+$")) serverPort = Integer.parseInt(portString);
			else {
				serverPort = defaultPort;
				preferencesUpdateRequired = true;
			}
			
			String autoCheckUpdatesString = document.getElementsByTagName(domTagAutoCheckUpdates).item(0).getTextContent();
			if("true".equals(autoCheckUpdatesString)) autoCheckUpdates = true;
			else if("false".equals(autoCheckUpdatesString)) autoCheckUpdates = false;
			else {
				autoCheckUpdates = defaultAutoCheckUpdates;
				preferencesUpdateRequired = true;
			}
			
			String scanFrequencyString = document.getElementsByTagName(domTagScanFrequency).item(0).getTextContent();
			if(scanFrequencyString.matches("^\\d+\\.\\d+$")) scanFrequency = Float.parseFloat(scanFrequencyString);
			else {
				scanFrequency = defaultScanFrequency;
				preferencesUpdateRequired = true;
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
	
	private static boolean createPreferences() {
		//Setting the default values
		serverPort = defaultPort;
		
		//Writing the preferences to disk
		return savePreferences();
	}
	
	private static boolean savePreferences() {
		try {
			//Creating the document
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			
			//Building the XML structure
			Element rootElement = document.createElement(domTagRoot);
			document.appendChild(rootElement);
			
			{
				Element element = document.createElement(domTagSchemaVer);
				element.setTextContent(Integer.toString(SCHEMA_VERSION));
				rootElement.appendChild(element);
			}
			
			{
				Element element = document.createElement(domTagPort);
				element.setTextContent(Integer.toString(serverPort));
				rootElement.appendChild(element);
			}
			
			{
				Element element = document.createElement(domTagAutoCheckUpdates);
				element.setTextContent(Boolean.toString(autoCheckUpdates));
				rootElement.appendChild(element);
			}
			
			{
				Element element = document.createElement(domTagScanFrequency);
				element.setTextContent(Float.toString(scanFrequency));
				rootElement.appendChild(element);
			}
			
			//Writing the XML document
			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(prefFile));
		} catch(ParserConfigurationException | TransformerException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.SEVERE, "Couldn't create new XML document", exception);
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	private static void applyPreferenceChanges() {
		{
			//Getting the port text
			String portText = pwTextPort.getText();
			if(!portText.isEmpty()) {
				//Updating the port
				serverPort = Integer.parseInt(portText); //Input is forced to be numerical due to input filter
			}
		}
		
		{
			//Getting the auto update
			boolean autoCheckUpdatesControl = pwButtonAutoUpdate.getSelection();
			if(autoCheckUpdates != autoCheckUpdatesControl) {
				autoCheckUpdates = autoCheckUpdatesControl;
				
				//Updating the update manager
				if(autoCheckUpdates) UpdateManager.startUpdateChecker();
				else UpdateManager.stopUpdateChecker();
			}
		}
		
		{
			//Getting the port text
			String scanFrequencyText = pwTextScanFrequency.getText();
			if(!scanFrequencyText.isEmpty()) {
				//Updating the port
				scanFrequency = Float.parseFloat(scanFrequencyText); //Input is forced to be numerical due to input filter
			}
		}
		
		//Saving the preferences
		savePreferences();
		
		//Updating the database manager
		DatabaseManager databaseManager = DatabaseManager.getInstance();
		if(databaseManager != null) databaseManager.scannerThread.updateScanFrequency((int) (scanFrequency * 1000));
		
		//Restarting the server
		Main.restartServer();
	}
	
	static boolean checkFirstRun() {
		//Exiting if the document doesn't exist
		if(!prefFile.exists()) throw new RuntimeException("Preference file doesn't exist!");
		
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
				exception.printStackTrace();
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
			pwTextPort.setText(Integer.toString(serverPort));
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
			pwButtonAutoUpdate.setSelection(autoCheckUpdates);
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
			
			pwTextScanFrequency.setText(Float.toString(scanFrequency));
			
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
		//Loading the passwords
		String[] passwords = loadPasswords();
		
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
			passText = new Text(shell, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
			passText.setText(Constants.getDelimitedString(passwords, "\n"));
			
			GridData passTextGD = new GridData();
			passTextGD.horizontalAlignment = GridData.FILL;
			passTextGD.verticalAlignment = GridData.FILL;
			passTextGD.grabExcessVerticalSpace = true;
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
			acceptButton.addListener(SWT.Selection, event -> {
				//Retrieving the lines and filtering out the empty ones
				LinkedList<String> filterList = new LinkedList<>();
				Collections.addAll(filterList, passText.getText().split("\n"));
				for(ListIterator<String> iterator = filterList.listIterator(); iterator.hasNext();) if(iterator.next().isEmpty()) iterator.remove();
				String[] newPasswords = filterList.toArray(new String[0]);
				
				//Saving the new passwords
				if(!Arrays.equals(passwords, newPasswords)) savePasswords(newPasswords);
				
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
		shell.setSize(300, 200);
		shell.open();
	}
	
	private static String[] loadPasswords() {
		//Checking if the users file exists
		if(userFile.exists()) {
			try {
				//Reading the file
				List<String> list = Files.readAllLines(userFile.toPath());
				for(ListIterator<String> iterator = list.listIterator(); iterator.hasNext();) {
					String line = iterator.next();
					try {
						iterator.set(new String(Base64.getDecoder().decode(line)));
					} catch(IllegalArgumentException exception) {
						//Logging a warning
						Main.getLogger().log(Level.WARNING, "Failed to decode password line (" + line + ")", exception);
						
						//Removing the item
						iterator.remove();
					}
				}
				
				//Returning the list
				return list.toArray(new String[0]);
			} catch(IOException exception) {
				//Printing the stack trace
				Main.getLogger().log(Level.SEVERE, "Failed to read users file at " + userFile.getPath(), exception);
				
				//Returning an empty list
				return new String[0];
			}
		} else {
			//Writing the default password list to disk
			savePasswords(defaultPasswords);
			
			//Returning the default password list
			return defaultPasswords;
		}
	}
	
	private static void savePasswords(String[] list) {
		//Creating the print writer
		try(PrintWriter writer = new PrintWriter(userFile, textEncoding)) {
			//Writing the passwords
			for(String line : list) writer.println(Base64.getEncoder().encodeToString(line.getBytes()));
		} catch(FileNotFoundException | UnsupportedEncodingException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.SEVERE, "Failed to write users file at " + userFile.getPath(), exception);
		}
	}
	
	static boolean matchPassword(String comparePass) {
		//Returning true if the file doesn't exist
		if(!userFile.exists()) return true;
		
		//Preparing the password containers
		byte[] comparePassBytes = comparePass.getBytes();
		byte[] storedPassBytes = new byte[0];
		
		//Reading the file
		try(BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
			//Iterating over the lines and returning true if one of them matches
			String line;
			while((line = reader.readLine()) != null) {
				try {
					storedPassBytes = Base64.getDecoder().decode(line);
					if(Arrays.equals(comparePassBytes, storedPassBytes)) return true;
				} catch(IllegalArgumentException exception) {
					//Logging a warning
					Main.getLogger().log(Level.WARNING, "Failed to decode password line (" + line + ")", exception);
				}
			}
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.SEVERE, "Failed to read users file at " + userFile.getPath(), exception);
			
			//Returning false
			return false;
		} finally {
			//Clearing the password containers
			byte zero = 0;
			Arrays.fill(comparePassBytes, zero);
			Arrays.fill(storedPassBytes, zero);
		}
		
		//Returning false
		return false;
	}
	
	static int getServerPort() {
		return serverPort;
	}
	
	static boolean getAutoCheckUpdates() {
		return autoCheckUpdates;
	}
	
	static float getScanFrequency() {
		return scanFrequency;
	}
}
package me.tagavari.airmessage.server;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

public class PreferencesManager {
	//Creating the reference values
	private static final int SCHEMA_VERSION = 1;
	
	private static final File prefFile = new File(Constants.applicationSupportDir, "prefs.xml");
	private static final File userFile = new File(Constants.applicationSupportDir, "users.xml");
	private static final int defaultPort = 1359;
	private static final String defaultPassword = "cookiesandmilk";
	
	private static final String domTagRoot = "Preferences";
	private static final String domTagSchemaVer = "SchemaVer";
	private static final String domTagPort = "Port";
	
	//Creating the preference values
	private static int serverPort;
	
	//Creating the other values
	private static String deviceSerial = "000000000000";
	private static Cipher cipher;
	
	static boolean prepare() {
		try {
			//Setting the cipher
			cipher = Cipher.getInstance("AES");
			
			//Returning true
			return true;
		} catch(NoSuchAlgorithmException | NoSuchPaddingException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.SEVERE, "Couldn't get cipher instance", exception);
			
			//Returning false
			return false;
		}
	}
	
	static boolean loadPreferences() {
		//Attempting to get the device's serial number
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
				if(!errorLines) deviceSerial = inputReader.readLine();
			}
		} catch(IOException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, "Couldn't read device serial number", exception);
		}
		
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
			
			//Checking if the document's schema version is valid
			if(schemaValid) {
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
			
			//Updating the preferences if it has been requested
			if(preferencesUpdateRequired) savePreferences();
			
			//Returning true
			return true;
		} else {
			//Creating the preferences
			return createPreferences();
		}
	}
	
	static boolean createPreferences() {
		//Setting the default values
		serverPort = defaultPort;
		
		//Writing the preferences to disk
		return savePreferences();
	}
	
	static boolean savePreferences() {
		try {
			//Creating the document
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			
			//Building the XML structure
			Element rootElement = document.createElement(domTagRoot);
			document.appendChild(rootElement);
			
			{
				Element schemaVerElement = document.createElement(domTagSchemaVer);
				schemaVerElement.setTextContent(Integer.toString(SCHEMA_VERSION));
				rootElement.appendChild(schemaVerElement);
			}
			
			{
				Element portElement = document.createElement(domTagPort);
				portElement.setTextContent(Integer.toString(serverPort));
				rootElement.appendChild(portElement);
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
	
	static void openPrefsWindow() {
		//Creating the shell
		Shell shell = new Shell(UIHelper.getDisplay(), SWT.TITLE);
		shell.setText(I18N.i.title_preferences());
		
		//Configuring the layouts
		GridLayout shellGL = new GridLayout(1, false);
		shellGL.marginLeft = shellGL.marginRight = shellGL.marginTop = shellGL.marginBottom = shellGL.marginWidth = shellGL.marginHeight = 0;
		shellGL.verticalSpacing = 5;
		shell.setLayout(shellGL);
		
		//Configuring the preferences
		Composite prefContainer = new Composite(shell, SWT.NONE);
		GridLayout prefContainerGL = new GridLayout(2, false);
		prefContainerGL.marginLeft = 100;
		prefContainerGL.marginRight = 50;
		prefContainerGL.marginTop = UIHelper.windowMargin;
		prefContainerGL.marginBottom = UIHelper.windowMargin;
		prefContainerGL.verticalSpacing = 5;
		prefContainer.setLayout(prefContainerGL);
		
		{
			Label portLabel = new Label(prefContainer, SWT.NONE);
			Composite prefGroup = new Composite(prefContainer, SWT.NONE);
			Text text = new Text(prefGroup, SWT.BORDER);
			
			portLabel.setText(I18N.i.pref_port());
			GridData labelGD = new GridData(GridData.END, GridData.BEGINNING, false, false);
			labelGD.verticalIndent = 2;
			portLabel.setLayoutData(labelGD);
			
			/* RowLayout prefGroupRL = new RowLayout();
			prefGroupRL.wrap = false;
			prefGroupRL.pack = false;
			prefGroupRL.justify = false;
			prefGroupRL.type = SWT.VERTICAL;
			prefGroupRL.marginTop = prefGroupRL.marginBottom = prefGroupRL.marginLeft = prefGroupRL.marginRight = 0; */
			GridLayout prefGroupGL = new GridLayout(1, false);
			prefGroupGL.marginWidth = prefGroupGL.marginHeight = 0;
			
			prefGroup.setLayout(prefGroupGL);
			
			text.setTextLimit(5);
			text.addVerifyListener(event -> {
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
			text.setLayoutData(textGD);
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
			prefsButton.addListener(SWT.Selection, event -> {
				openPrefsPasswordWindow(shell);
			});
		}
		
		//Adding the divider
		{
			Label divider = new Label(shell, SWT.HORIZONTAL);
			divider.setBackground(UIHelper.getDisplay().getSystemColor(SWT.COLOR_GRAY));
			GridData dividerGD = new GridData(GridData.FILL_HORIZONTAL);
			dividerGD.heightHint = 1;
			divider.setLayoutData(dividerGD);
		}
		
		Composite buttonContainer = new Composite(shell, SWT.NONE);
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
			shell.setDefaultButton(acceptButton);
			
			Button discardButton = new Button(buttonContainer, SWT.PUSH);
			discardButton.setText(I18N.i.button_cancel());
			FormData discardButtonFD = new FormData();
			if(discardButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) discardButtonFD.width = UIHelper.minButtonWidth;
			discardButtonFD.right = new FormAttachment(acceptButton);
			discardButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			discardButton.setLayoutData(discardButtonFD);
		}
		
		//Packing the shell
		shell.pack();
		shell.setMinimumSize(500, shell.getMinimumSize().y);
		
		//Centering the window
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = shell.getBounds();
		shell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		shell.open();
	}
	
	static void openPrefsPasswordWindow(Shell parentShell) {
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
		Table table;
		Button removeItemButton;
		
		{
			//Creating the list label
			Label listLabel = new Label(shell, SWT.NONE);
			listLabel.setText(I18N.i.pref_passwords());
			
			//Creating the list
			table = new Table(shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.FULL_SELECTION);
			
			//Creating the table editor
			TableEditor editor = new TableEditor(table);
			editor.horizontalAlignment = SWT.LEFT;
			editor.grabHorizontal = true;
			editor.grabVertical = true;
			
			//Adding the table selection listener
			table.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent selectionEvent) {
					//Checking if the previous editor is still intact
					Control oldEditor = editor.getEditor();
					if(oldEditor != null && !oldEditor.isDisposed()) {
						//Applying the edited text
						Text text = (Text)editor.getEditor();
						editor.getItem().setText(text.getText());
						
						//Disposing of the editor
						oldEditor.dispose();
					}
					
					//Getting the table item
					TableItem item = (TableItem) selectionEvent.item;
					if(item == null) return;
					
					//Creating the text editor
					Text newEditor = new Text(table, SWT.NONE);
					newEditor.setText(item.getText());
					
					//Recording the original text and clearing the item text
					String originalText = item.getText();
					item.setText("");
					
					//Adding the modification listener to resize the text field
					newEditor.addModifyListener(modifyEvent -> UIHelper.packControl((Text) modifyEvent.widget, ((Text) modifyEvent.widget).getText(), 6));
					
					//Adding a key listener to track submit / discard events
					Listener textListener = textEvent -> {
						//Getting the editor
						Text text = (Text) editor.getEditor();
						
						//Checking if the event is a lost focus
						if(textEvent.type == SWT.FocusOut) {
							//Applying the changes
							editor.getItem().setText(text.getText());
							
							//Disposing of the editor
							newEditor.dispose();
							
							//Setting the text editor as closed
							textEditorOpen.value = Boolean.FALSE;
							
							//Focusing the table
							table.setFocus();
						}
						//Otherwise checking if the event is a traverse
						else if(textEvent.type == SWT.Traverse) {
							//Checking if the traverse type is a confirmation (return)
							if(textEvent.detail == SWT.TRAVERSE_RETURN) {
								//Applying the changes
								editor.getItem().setText(text.getText());
								
								//Disposing of the editor
								newEditor.dispose();
								
								//Setting the text editor as closed
								textEditorOpen.value = Boolean.FALSE;
								
								//Focusing the table
								table.setFocus();
							}
							//Otherwise checking if the traverse type is a discard (escape)
							else if(textEvent.detail == SWT.TRAVERSE_ESCAPE) {
								//Reverting the changes
								editor.getItem().setText(originalText);
								
								//Disposing of the editor
								newEditor.dispose();
								
								//Setting the text editor as closed
								textEditorOpen.value = Boolean.FALSE;
								
								//Focusing the table
								table.setFocus();
							}
						}
					};
					
					newEditor.addListener(SWT.FocusOut, textListener);
					newEditor.addListener(SWT.Traverse, textListener);
					
					//Adding a key listener to track enter / discard keys
					/* newEditor.addKeyListener(new KeyAdapter() {
						public void keyPressed(KeyEvent event) {
							//Checking if the event is confirming the changes (return)
							if(event.character == SWT.CR) {
								//Applying the changes
								Text text = (Text) editor.getEditor();
								editor.getItem().setText(text.getText());
								
								//Disposing of the editor
								newEditor.dispose();
								
								//Setting the text editor as closed
								textEditorOpen.value = Boolean.FALSE;
								
								//Focusing the table
								table.setFocus();
							}
							//Otherwise checking if the event is discarding the changes (escape)
							if(event.character == SWT.ESC) {
								//Reverting the changes
								Text text = (Text) editor.getEditor();
								editor.getItem().setText(originalText);
								
								//Disposing of the editor
								newEditor.dispose();
								
								//Setting the text editor as closed
								textEditorOpen.value = Boolean.FALSE;
								
								//Focusing the table
								table.setFocus();
							}
						}
					}); */
					
					//Enabling the editor
					newEditor.selectAll();
					newEditor.setFocus();
					editor.setEditor(newEditor, item, 0);
					newEditor.pack();
					
					//Setting the text editor as open
					textEditorOpen.value = Boolean.TRUE;
				}
			});
			
			GridData tableGD = new GridData();
			tableGD.horizontalAlignment = GridData.FILL;
			tableGD.grabExcessVerticalSpace = true;
			tableGD.grabExcessHorizontalSpace = true;
			table.setLayoutData(tableGD);
			
			for (int i=0; i<10; i++) {
				TableItem item = new TableItem (table, 0);
				item.setText ("Item " + i);
			}
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
				//TODO save data
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
			discardButton.addListener(SWT.Selection, event -> {
				//TODO discard data
				shell.close();
			});
			
			//Fetching the image resources
			Image addImage = new Image(UIHelper.getDisplay(), PreferencesManager.class.getClassLoader().getResourceAsStream("icon_add.png"));
			Image removeImage = new Image(UIHelper.getDisplay(), PreferencesManager.class.getClassLoader().getResourceAsStream("icon_remove.png"));
			
			//Adding a listener to the shell to release the resources
			shell.addListener(SWT.Close, closeEvent -> {
				addImage.dispose();
				removeImage.dispose();
			});
			
			//Adding the list add / remove buttons
			Button addItemButton = new Button(buttonContainer, SWT.FLAT);
			addItemButton.setImage(addImage);
			FormData addItemButtonFD = new FormData();
			addItemButtonFD.width = addItemButtonFD.height = 25;
			addItemButtonFD.left = new FormAttachment(0);
			addItemButtonFD.top = new FormAttachment(50, -addItemButtonFD.height / 2);
			addItemButton.setLayoutData(addItemButtonFD);
			
			removeItemButton = new Button(buttonContainer, SWT.FLAT);
			removeItemButton.setImage(removeImage);
			FormData removeItemButtonFD = new FormData();
			removeItemButtonFD.width = removeItemButtonFD.height = 25;
			removeItemButtonFD.left = new FormAttachment(addItemButton);
			removeItemButtonFD.top = new FormAttachment(50, -removeItemButtonFD.height / 2);
			removeItemButton.setLayoutData(removeItemButtonFD);
		}
		
		//Opening the dialog
		shell.pack();
		shell.setSize(300, 200);
		shell.open();
	}
}
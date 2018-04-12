package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UpdateManager {
	//Creating the reference values
	private static final int schemaVer = 1;
	private static final URL updateURL = makeURL("https://airmessage.org/update/server/1/");
	
	//Creating the class values
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private static final Runnable runUpdateCheck = () -> {
		//Getting the object
		JSONObject jRoot;
		try(Scanner scanner = new Scanner(updateURL.openStream())) {
			scanner.useDelimiter("\\A");
			
			if(!scanner.hasNext()) return;
			String result = scanner.next();
			jRoot = new JSONObject(result);
		} catch(IOException exception) {
			exception.printStackTrace();
			return;
		}
		
		try {
			//Finding the first valid release
			JSONObject jLatestRelease = null;
			JSONArray jArrayReleases = jRoot.getJSONArray("releases");
			for(int i = 0; i < jArrayReleases.length(); i++) {
				JSONObject jRelease = jArrayReleases.getJSONObject(i);
				if(Constants.compareVersions(Constants.getSystemVersion(), Constants.parseVersionString(jRelease.getString("os_version_requirement"))) < 0) continue;
				jLatestRelease = jRelease;
				break;
			}
			
			//Returning if no release was found, or no upgrade is needed
			if(jLatestRelease == null || jLatestRelease.getInt("version_code") <= Constants.SERVER_VERSION_CODE) return;
			
			//Fetching the locales of the release notes
			HashMap<Locale, String> releaseNotes = new HashMap<>(); //Locale, message
			JSONArray jArrayNotes = jLatestRelease.getJSONArray("notes");
			for(int j = 0; j < jArrayNotes.length(); j++) {
				JSONObject jNotes = jArrayNotes.getJSONObject(j);
				releaseNotes.put(Locale.forLanguageTag(jNotes.getString("lang")), jNotes.getString("message"));
			}
			
			//Compiling the system locales into a language range list
			List<Locale.LanguageRange> languageRangeList = new ArrayList<>();
			//for(Locale locale : Locale.getAvailableLocales()) languageRangeList.add(new Locale.LanguageRange(locale.toLanguageTag()));
			languageRangeList.add(new Locale.LanguageRange(Locale.getDefault().toLanguageTag()));
			languageRangeList.add(new Locale.LanguageRange(Locale.ENGLISH.toLanguageTag()));
			
			//Finding a target locale
			Locale targetLocale = null;
			List<Locale> filterResults = Locale.filter(languageRangeList, releaseNotes.keySet());
			if(!filterResults.isEmpty()) targetLocale = filterResults.get(0);
			else targetLocale = Locale.lookup(languageRangeList, releaseNotes.keySet());
			if(targetLocale == null) throw new JSONException("Empty release notes language");
			
			//Getting the release information
			final String relVerName = jLatestRelease.getString("version_name");
			final String relTargetLink = jLatestRelease.getString("download-url");
			String relMessage = new String(Base64.getDecoder().decode(releaseNotes.get(targetLocale)));
			final String relMessageHTML = HtmlRenderer.builder().build().render(Parser.builder().build().parse(relMessage));
			
			//Showing the update window
			UIHelper.getDisplay().asyncExec(() -> openUpdateWindow(relVerName, relMessageHTML, relTargetLink));
		} catch(Exception exception) {
			Sentry.capture(exception);
			exception.printStackTrace();
			
			throw exception;
		}
	};
	private static ScheduledFuture<?> handleUpdateCheck = null;
	
	static void startUpdateChecker() {
		handleUpdateCheck = scheduler.scheduleAtFixedRate(runUpdateCheck, 0, 1, TimeUnit.DAYS);
	}
	
	static void stopUpdateChecker() {
		handleUpdateCheck.cancel(false);
	}
	
	private static URL makeURL(String target) {
		try {
			return new URL(target);
		} catch(MalformedURLException exception) {
			exception.printStackTrace();
			Sentry.capture(exception);
			
			throw new IllegalArgumentException(exception);
		}
	}
	
	private static void openUpdateWindow(String newVer, String releaseNotes, String targetLink) {
		//Creating the shell
		Shell shell = new Shell(UIHelper.getDisplay(), SWT.TITLE | SWT.CLOSE | SWT.MIN);
		//Shell shell = new Shell(UIHelper.getDisplay());
		shell.setText(I18N.i.title_update());
		shell.setSize(550, 300);
		
		//Configuring the layout
		{
			GridLayout shellGL = new GridLayout();
			shellGL.numColumns = 1;
			shellGL.marginTop = UIHelper.windowMargin;
			shellGL.marginBottom = UIHelper.windowMargin;
			shellGL.marginLeft = UIHelper.windowMargin;
			shellGL.marginRight = UIHelper.windowMargin;
			shellGL.verticalSpacing = 8;
			shell.setLayout(shellGL);
			
			Label labelTitle = new Label(shell, SWT.WRAP);
			labelTitle.setText(I18N.i.message_updateAvailable_title(Constants.APP_NAME));
			labelTitle.setFont(UIHelper.getFont(labelTitle.getFont(), 14, SWT.BOLD));
			GridData labelTitleGD = new GridData();
			labelTitleGD.grabExcessHorizontalSpace = true;
			labelTitleGD.horizontalAlignment = GridData.FILL;
			labelTitle.setLayoutData(labelTitleGD);
			
			Label labelDescription = new Label(shell, SWT.WRAP);
			labelDescription.setText(I18N.i.message_updateAvailable_description(Constants.APP_NAME, Constants.SERVER_VERSION, newVer));
			GridData labelDescriptionGD = new GridData();
			labelDescriptionGD.grabExcessHorizontalSpace = true;
			labelDescriptionGD.horizontalAlignment = GridData.FILL;
			labelDescription.setLayoutData(labelDescriptionGD);
			
			Label labelNotes = new Label(shell, SWT.WRAP);
			labelNotes.setText(I18N.i.message_releaseNotes());
			labelNotes.setFont(UIHelper.getFont(labelNotes.getFont(), -1, SWT.BOLD));
			GridData labelNotesGD = new GridData();
			labelNotesGD.grabExcessHorizontalSpace = true;
			labelNotesGD.horizontalAlignment = GridData.FILL;
			labelNotes.setLayoutData(labelNotesGD);
			
			Browser browserNotes = new Browser(shell, SWT.BORDER);
			browserNotes.setText(releaseNotes);
			browserNotes.setMenu(new Menu(browserNotes));
			browserNotes.addProgressListener(new ProgressListener() {
				@Override
				public void changed(ProgressEvent event) {}
				
				@Override
				public void completed(ProgressEvent event) {
					browserNotes.execute("document.getElementsByTagName('body')[0].style.fontFamily = 'sans-serif';");
					browserNotes.execute("document.getElementsByTagName('body')[0].style.fontSize = " + UIHelper.getDisplay().getSystemFont().getFontData()[0].getHeight() + ";");
				}
			});
			GridData browserNotesGD = new GridData();
			browserNotesGD.grabExcessHorizontalSpace = browserNotesGD.grabExcessVerticalSpace = true;
			browserNotesGD.horizontalAlignment = browserNotesGD.verticalAlignment = GridData.FILL;
			browserNotesGD.minimumHeight = 100;
			browserNotesGD.heightHint = 100;
			browserNotes.setLayoutData(browserNotesGD);
		}
		
		//Configuring the buttons
		{
			Composite buttonContainer = new Composite(shell, SWT.NONE);
			GridData buttonContainerGD = new GridData();
			buttonContainerGD.grabExcessHorizontalSpace = true;
			buttonContainerGD.horizontalAlignment = GridData.FILL;
			buttonContainer.setLayoutData(buttonContainerGD);
			FormLayout buttonContainerFL = new FormLayout();
			buttonContainer.setLayout(buttonContainerFL);
			
			Button acceptButton = new Button(buttonContainer, SWT.PUSH);
			acceptButton.setText(I18N.i.button_installUpdate());
			FormData acceptButtonFD = new FormData();
			if(acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) acceptButtonFD.width = UIHelper.minButtonWidth;
			acceptButtonFD.right = new FormAttachment(100);
			acceptButtonFD.top = new FormAttachment(50, -acceptButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).y / 2);
			acceptButton.setLayoutData(acceptButtonFD);
			acceptButton.addListener(SWT.Selection, event -> Program.launch(targetLink));
			shell.setDefaultButton(acceptButton);
			
			Button ignoreButton = new Button(buttonContainer, SWT.PUSH);
			ignoreButton.setText(I18N.i.button_installUpdate_later());
			FormData ignoreButtonFD = new FormData();
			if(ignoreButton.computeSize(SWT.DEFAULT, SWT.DEFAULT).x < UIHelper.minButtonWidth) ignoreButtonFD.width = UIHelper.minButtonWidth;
			ignoreButtonFD.right = new FormAttachment(acceptButton);
			ignoreButtonFD.top = new FormAttachment(acceptButton, 0, SWT.CENTER);
			ignoreButton.setLayoutData(ignoreButtonFD);
			ignoreButton.addListener(SWT.Selection, event -> shell.close());
		}
		
		//Packing the shell
		//shell.pack();
		
		//Getting the bounds
		Rectangle screenBounds = UIHelper.getDisplay().getPrimaryMonitor().getBounds();
		Rectangle windowBounds = shell.getBounds();
		
		//Centering the window
		shell.setLocation(screenBounds.x + (screenBounds.width - windowBounds.width) / 2, screenBounds.y + (screenBounds.height - windowBounds.height) / 2);
		
		//Opening the shell
		shell.open();
	}
}
package me.tagavari.airmessage.server;

import io.sentry.Sentry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
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
		System.out.println("Starting update checker");
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
			for(int j = 0; j < jArrayReleases.length(); j++) {
				JSONObject jRelease = jArrayReleases.getJSONObject(j);
				if(Constants.compareVersions(Constants.getSystemVersion(), Constants.parseVersionString(jRelease.getString("os_version_requirement"))) < 0) continue;
				jLatestRelease = jRelease;
				break;
			}
			
			//Returning if no release was found, or no upgrade is needed
			if(jLatestRelease == null || jLatestRelease.getInt("version_code") <= Constants.SERVER_VERSION_CODE) return;
			
			//TODO request user to update
			System.out.println("Update found!");
		} catch(JSONException exception) {
			Sentry.capture(exception);
			exception.printStackTrace();
			
			return;
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
}
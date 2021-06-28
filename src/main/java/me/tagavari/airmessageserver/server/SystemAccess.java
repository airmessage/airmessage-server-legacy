package me.tagavari.airmessageserver.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SystemAccess {
	public static String processForResult(String command) {
		try {
			Process process = Runtime.getRuntime().exec(command);
			
			if(process.waitFor() == 0) {
				//Reading and returning the input
				BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
				return in.lines().collect(Collectors.joining());
			} else {
				//Logging the error
				try(BufferedReader in = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
					String errorOutput = in.lines().collect(Collectors.joining());
					Main.getLogger().log(Level.WARNING, "Unable to read device name: " + errorOutput);
				}
			}
		} catch(IOException | InterruptedException exception) {
			//Printing the stack trace
			Main.getLogger().log(Level.WARNING, exception.getMessage(), exception);
		}
		
		return null;
	}
	
	public static String readDeviceName() {
		return processForResult("scutil --get ComputerName");
	}
	
	public static boolean isProcessTranslated() {
		return "1".equals(processForResult("sysctl -n sysctl.proc_translated"));
	}
	
	public static String readProcessorArchitecture() {
		return processForResult("uname -p");
	}
}
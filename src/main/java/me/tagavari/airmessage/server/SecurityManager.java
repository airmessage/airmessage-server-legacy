package me.tagavari.airmessage.server;

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import javax.net.ssl.*;
import java.io.*;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.stream.Stream;

public class SecurityManager {
	//Creating the variables
	static final ArrayList<String> passwords = new ArrayList<>();
	
	/* static boolean generateFiles() {
		try {
			//Creating the server keystore
			KeyStore serverKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			char[] password = "password".toCharArray();
			serverKeyStore.load(null, password);
			
			//Generating the certificate with 2048 bits
			CertAndKeyGen certGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
			certGen.generate(2048);
			
			//Adding the certificate info
			X509Certificate cert = certGen.getSelfCertificate(
					new X500Name("CN=My Application,O=My Organisation,L=My City,C=DE"),
					60L * 60L * 24L * 365L * 100L); //100 years
			
			//Adding the certificate to the keystore
			serverKeyStore.setKeyEntry("server", certGen.getPrivateKey(), password, new X509Certificate[] {cert});
			
			//Writing the server keystore to disk
			FileOutputStream outputStream = new FileOutputStream(Constants.serverKeystoreName);
			
			serverKeyStore.store(outputStream, password);
			outputStream.close();
			
			//Writing the server certificate to disk
			outputStream = new FileOutputStream(Constants.certificateName);
			outputStream.write(cert.getEncoded());
			outputStream.close();
			
			KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			clientKeyStore.load(null, "password".toCharArray());
			clientKeyStore.setCertificateEntry("server", cert);
			
			outputStream = new FileOutputStream(Constants.clientKeystoreName);
			clientKeyStore.store(outputStream, "password".toCharArray());
			outputStream.close();
		} catch (Exception exception) {
			//Logging an error
			System.out.println("Failed to create keystore: " + exception.getMessage());
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	} */
	
	static boolean loadCredentials() {
		//Getting the file
		File file = new File(Constants.userList);
		
		//Checking if the file doesn't exist
		if(!file.exists()) {
			//Creating the default credential file
			boolean result = createDefaultCredentialFile(file);
			
			//Logging a message
			Main.getLogger().warning("Edit the password file (" + Constants.userList + ") to add, edit or remove passwords");
			Main.getLogger().warning("Since it didn't previously exist, authentication is disabled for this session");
			
			//Returning the result
			return result;
		}
		
		//Checking if the file is a directory
		if(file.isDirectory()) {
			//Logging a message
			Main.getLogger().warning("User file " + Constants.userList + " is a directory");
			Main.getLogger().warning("Please remove it to enable authentication");
			
			//Returning false
			return false;
		}
		
		//Reading the credentials
		return readCredentials(file);
	}
	
	private static boolean createDefaultCredentialFile(File file) {
		//Creating the file
		try {
			file.createNewFile();
		} catch(IOException exception) {
			//Printing the exception
			Main.getLogger().warning("Couldn't make user file: " + exception.getMessage());
			Main.getLogger().warning("Authentication is disabled");
			
			//Returning false
			return false;
		}
		
		//Getting the input stream
		InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(Constants.resourceDefaultCredentialList);
		
		try {
			//Getting the output stream
			OutputStream outputStream = new FileOutputStream(file);
			byte[] buffer = new byte[1024];
			int bytesRead;
			while((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
		} catch(Exception exception) {
			//Printing the exception
			Main.getLogger().warning("Couldn't write to user file: " + exception.getMessage());
			Main.getLogger().warning("Authentication is disabled");
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	}
	
	private static boolean readCredentials(File file) {
		//Reading the file
		Stream<String> fileStream;
		try {
			fileStream = Files.lines(file.toPath());
		} catch(IOException exception) {
			Main.getLogger().warning("Couldn't read user file (" + Constants.userList + "): " + exception.getMessage());
			Main.getLogger().warning("The file will be created anew if the file is deleted and the program is restarted");
			Main.getLogger().warning("Authentication is disabled");
			
			//Returning false
			return false;
		}
		
		//Adding the passwords
		fileStream.forEach(line -> {
			if(!line.startsWith("#")) passwords.add(line);
		});
		
		//Closing the stream
		fileStream.close();
		
		//Logging a message if there are no passwords
		if(passwords.isEmpty()) Main.getLogger().warning("No passwords could be found, authentication is disabled");
		
		//Returning true
		return true;
	}
	
	static boolean authenticate(String password) {
		//Returning true if the password invalid or there are no passwords
		if(password == null || passwords.isEmpty()) return true;
		
		//Returning if is there is a matching password
		return passwords.contains(password);
	}
	
	static SSLContext createSSLContext() {
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
					public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
				}
		};
		
		/* try {
			SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new SecureRandom());
			return sslContext;
		} catch(NoSuchAlgorithmException | KeyManagementException exception) {
			exception.printStackTrace();
			return null;
		} */
		
		try {
			//Creating the keystore
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, "password".toCharArray());
			
			//Generating the certificate
			CertAndKeyGen certGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
			certGen.generate(2048);
			long validSecs = 60L * 60L * 24L * 365L * 100L; //Valid for 100 years
			X509Certificate cert = certGen.getSelfCertificate(new X500Name("CN=My Application,O=My Organisation,L=My City,C=DE"), validSecs);
			
			//Adding the certificate to the keystore
			keyStore.setKeyEntry("server", certGen.getPrivateKey(), "password".toCharArray(), new X509Certificate[]{cert});
			
			/* TrustManager[] trustAllCerts = new TrustManager[] {
					new X509TrustManager() {
						public java.security.cert.X509Certificate[] getAcceptedIssuers() {
							return new X509Certificate[0];
						}
						public void checkClientTrusted(
								java.security.cert.X509Certificate[] certs, String authType) {
						}
						public void checkServerTrusted(
								java.security.cert.X509Certificate[] certs, String authType) {
						}
					}
			}; */
			
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); //KeyManagerFactory.getDefaultAlgorithm()
			kmf.init(keyStore, "password".toCharArray());
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509"); //TrustManagerFactory.getDefaultAlgorithm()
			tmf.init(keyStore);
			
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
			return sslContext;
		} catch(Exception exception) {
			exception.printStackTrace();
			return null;
		}
	}
}
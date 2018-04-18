package me.tagavari.airmessage.server;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

public class SecurityManager {
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
			exception.printStackTrace();
			
			//Returning false
			return false;
		}
		
		//Returning true
		return true;
	} */
	
	/* static boolean loadCredentials() {
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
	} */
	
	static X509Certificate selfSign(KeyPair keyPair, String subjectDN) throws OperatorCreationException, CertificateException, IOException
	{
		Provider bcProvider = new BouncyCastleProvider();
		Security.addProvider(bcProvider);
		
		long now = System.currentTimeMillis();
		Date startDate = new Date(now);
		
		X500Name dnName = new X500Name(subjectDN);
		BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number
		
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(startDate);
		calendar.add(Calendar.YEAR, 1); // <-- 1 Yr validity
		
		Date endDate = calendar.getTime();
		
		String signatureAlgorithm = "SHA256WithRSA"; // <-- Use appropriate signature algorithm based on your keyPair algorithm.
		
		ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
		
		JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());
		
		// Extensions --------------------------
		
		// Basic Constraints
		//BasicConstraints basicConstraints = new BasicConstraints(true); // <-- true for CA, false for EndEntity
		
		//certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.
		
		// -------------------------------------
		
		return new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));
	}
	
	/* private static X509Certificate getCertificate(KeyPair keys) {
		try {
			X500Name name = new X500Name(new X500Principal("CN=My Application,O=My Organisation,L=My City,C=DE").getName());
			X509v1CertificateBuilder certificateBuilder = new X509v1CertificateBuilder(
					name,
					new BigInteger(Long.toString(System.currentTimeMillis())),
					new Date(System.currentTimeMillis() - 1000L * 60L * 60L * 24L),
					new Date(System.currentTimeMillis() + 1000L * 60L * 60L * 24L * 365L),
					name,
					getPublicKeyInfo(keys.getPublic()));
			X509CertificateHolder certificateHolder = certificateBuilder.build(getSigner(keys));
			return new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(certificateHolder);
		} catch (CertificateException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static SubjectPublicKeyInfo getPublicKeyInfo(PublicKey publicKey) {
		if(!(publicKey instanceof RSAPublicKey)) throw new RuntimeException("publicKey is not an RSAPublicKey");
		
		RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
		
		try {
			return SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(new RSAKeyParameters(false, rsaPublicKey.getModulus(), rsaPublicKey.getPublicExponent()));
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}
	
	private static ContentSigner getSigner(KeyPair keys) {
		try {
			return new JcaContentSignerBuilder("SHA1WithRSA").setProvider(new BouncyCastleProvider()).build(
					keys.getPrivate());
		} catch (OperatorCreationException e) {
			throw new RuntimeException(e);
		}
	} */
	
	static SSLContext conjureSSLContext() {
		try {
			//Generating the key pair
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(2048, new SecureRandom());
			KeyPair keyPair = keyGen.generateKeyPair();
			
			//Creating a self-signed certificate
			X509Certificate certificate = selfSign(keyPair, "CN=My Application,O=My Organisation,L=My City,C=DE");
			
			//Generating a password
			char[] password = Constants.randomAlphaNumericString(16).toCharArray();
			
			//Creating the keystore
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, null);
			keyStore.setKeyEntry("server", keyPair.getPrivate(), password, new X509Certificate[]{certificate});
			
			//Creating the managers
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); //KeyManagerFactory.getDefaultAlgorithm()
			kmf.init(keyStore, password);
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); //TrustManagerFactory.getDefaultAlgorithm()
			tmf.init(keyStore);
			
			//Creating and returning the SSL context
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
			return sslContext;
		} catch(Exception exception) {
			exception.printStackTrace();
			return null;
		}
	}
	
	/* static SSLContext createSSLContext() {
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}
					public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
					public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
				}
		};
		
		try {
			
			//Generating the certificate
			CertAndKeyGen certGen = new CertAndKeyGen("RSA", "SHA256WithRSA", null);
			certGen.generate(2048);
			long validSecs = 60L * 60L * 24L * 365L * 100L; //Valid for 100 years
			X509Certificate cert = certGen.getSelfCertificate(new sun.security.x509.X500Name("CN=My Application,O=My Organisation,L=My City,C=DE"), validSecs);
			
			//Creating the keystore
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, "password".toCharArray());
			
			//Adding the certificate to the keystore
			keyStore.setKeyEntry("server", certGen.getPrivateKey(), "password".toCharArray(), new X509Certificate[]{cert});
			
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
	} */
	
	/* static SSLContext createSSLContext() {
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(null, "password".toCharArray());
			keyStore.setKeyEntry("server", X509);
			
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); //KeyManagerFactory.getDefaultAlgorithm()
			kmf.init(keyStore, "password".toCharArray());
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509"); //TrustManagerFactory.getDefaultAlgorithm()
			tmf.init(keyStore);
			
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
			return context;
			//return SSLContext.getDefault();
		} catch(NoSuchAlgorithmException | KeyStoreException | IOException | CertificateException | UnrecoverableKeyException | KeyManagementException exception) {
			exception.printStackTrace();
			return null;
		}
	} */
}
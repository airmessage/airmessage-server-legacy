package me.tagavari.airmessageserver.server;

import java.util.List;

public record UpdateRecord(
	int versionCode,
	String versionName,
	String osRequirement,
	List<Notes> notes,
	String urlIntel,
	String urlAppleSilicon,
	boolean externalDownload
) {
	public record Notes(
		String lang,
		String message
	) {}
}
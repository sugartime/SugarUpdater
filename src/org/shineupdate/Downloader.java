package org.shineupdate;

import com.google.common.base.Charsets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;

import java.net.URL;

import org.appkit.concurrent.ReportQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Downloader {

	//~ Enumerations ---------------------------------------------------------------------------------------------------

	public enum Status {VERSION, PROGRESS, DOWNLOADED_FILE, ERROR;
	}

	//~ Static fields/initializers -------------------------------------------------------------------------------------

	private static final Logger L = LoggerFactory.getLogger(Downloader.class);

	//~ Instance fields ------------------------------------------------------------------------------------------------

	private final ReportQueue queue = ReportQueue.create();

	//~ Constructors ---------------------------------------------------------------------------------------------------

	private Downloader() {}

	//~ Methods --------------------------------------------------------------------------------------------------------

	public ReportQueue getReports() {
		return this.queue;
	}

	public static Downloader create() {
		return new Downloader();
	}

	public void loadLatestVersion(final String url) {
		ShineUpdate.instance().executor.execute(
			new Runnable() {
					@Override
					public void run() {
						L.debug("downloading description from '{}'", url);

						Reader reader = null;
						try {
							reader = new BufferedReader(
									new InputStreamReader(new URL(url).openStream(), Charsets.UTF_8));

							/** read file from url **/
							StringBuilder sb = new StringBuilder();
							while (true) {

								char buffer[] = new char[1024];
								int readChars = reader.read(buffer, 0, 1024);
								if (readChars == -1) {
									break;
								} else {
									sb.append(buffer, 0, readChars);
								}
							}
							reader.close();

							/** parse into VersionDescription */
							queue.report(Status.VERSION, VersionDescription.parse(sb.toString()));

						} catch (final IOException e) {
							L.error(e.getMessage(), e);
							queue.report(Status.ERROR);

						} catch (final RuntimeException e) {
							L.error(e.getMessage(), e);
							queue.report(Status.ERROR);

						} finally {
							if (reader != null) {
								try {
									reader.close();
								} catch (final IOException e) {}
							}
						}
					}
				});
	}

	public void stopDownload() {}

	/** read file from url **/
	public void loadUpdate(final String url, final int totalSize) {
		ShineUpdate.instance().executor.execute(
			new Runnable() {
					@Override
					public void run() {

						InputStream in   = null;
						OutputStream out = null;
						try {

							File downloadTempFile = File.createTempFile(StaticConfig.TEMP_FILE_PREFIX, null);

							in	    = new BufferedInputStream(new URL(url).openStream());
							out = new BufferedOutputStream(new FileOutputStream(downloadTempFile));

							byte data[]  = new byte[StaticConfig.DOWNLOAD_BUFFER_SIZE];
							int progress = 0;
							int count;
							while ((count = in.read(data, 0, StaticConfig.DOWNLOAD_BUFFER_SIZE)) != -1) {
								out.write(data, 0, count);
								progress = progress + count;

								queue.report(Status.PROGRESS, totalSize, progress);
							}

							queue.report(Status.DOWNLOADED_FILE, downloadTempFile);

						} catch (final IOException e) {
							L.error(e.getMessage(), e);
							queue.report(Status.ERROR);

						} finally {
							if (in != null) {
								try {
									in.close();
								} catch (final IOException e) {}
							}
							if (out != null) {
								try {
									out.close();
								} catch (final IOException e) {}
							}
						}
					}
				});
	}
}
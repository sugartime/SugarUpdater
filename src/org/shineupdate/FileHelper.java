package org.shineupdate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;

public final class FileHelper {

	private static final Logger L = LoggerFactory.getLogger(FileHelper.class);

	public static String getSHA1Hash(final File file) {
		try {
			return Files.hash(file, Hashing.sha1()).toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static File unzipMacApplication(final File zippedFile) {
		try {
			File tempDir = Files.createTempDir();

			final ZipFile zipFile = new ZipFile(zippedFile);
			Enumeration<?> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				final ZipEntry entry = (ZipEntry) entries.nextElement();
				L.debug("{}: '{}'", entry.isDirectory() ? "dir" : "file", entry.getName());

				String name = tempDir.getAbsolutePath() + File.separator + entry.getName();
				File target = new File(name);
				if (entry.isDirectory()) {
					Preconditions.checkState(target.mkdirs(), "could not create directories: '%s'", target);
				} else {
					Files.createParentDirs(target);
					InputSupplier<InputStream> supplier = new InputSupplier<InputStream>() {
						@Override
						public InputStream getInput() throws IOException {
							return zipFile.getInputStream(entry);
						}
					};
					Files.copy(supplier, target);
				}
			}
			zipFile.close();

			/* Look for ".app" */
			/*
			for (File file : tempDir.listFiles()) {
				if (file.getName().endsWith(".app") && file.isDirectory()) {
					return file;
				}
			}
			*/
			
			for (File file : tempDir.listFiles()) {
				if (file.getName().endsWith(".exe")) {
					return file;
				}
			}


			Preconditions.checkState(false, "no .app found in tempdir: %s", tempDir);
			return null;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

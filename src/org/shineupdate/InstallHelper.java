package org.shineupdate;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import org.appkit.util.ResourceStreamSupplier;

public final class InstallHelper {

	//~ Methods --------------------------------------------------------------------------------------------------------

	public static String prepareCommand(final File downloadTempFile, final File application) {

		/* copy script to another tempfile */
		try {
			File helperFile = File.createTempFile(StaticConfig.TEMP_FILE_PREFIX + "helper-" + ShineUpdate.instance().applicationUID , ".jar");

			Files.copy(ResourceStreamSupplier.create().getSupplier("installhelper.jar"), helperFile);

			/* run installhelper with parameters */
			String b64Download = Base64.encodeBase64String(downloadTempFile.getAbsolutePath().getBytes());
			String b64Target   = Base64.encodeBase64String(application.getAbsolutePath().getBytes());
			String cmd		   = helperFile.getAbsolutePath() + " " + b64Download + " " + b64Target;

			return cmd;

		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(final String args[]) {
		try {
			Preconditions.checkArgument(args.length == 2, "Usage: <from> <to>");

			File download = new File(new String(Base64.decodeBase64(args[0])));
			File target   = new File(new String(Base64.decodeBase64(args[1])));
			Preconditions.checkArgument(download.exists(), "download doesn't exist '%s'", download);

			/* delete old */
			delete(target);

			/* copy new */
			copy(download, target);

			/* start new */
			Runtime.getRuntime().exec(target.getAbsolutePath());

		} catch (final IOException e) {
			e.printStackTrace();
		} catch (final RuntimeException e) {
			e.printStackTrace();
		}
	}

	private static void delete(final File rootPath) {

		List<File> workList = Lists.newArrayList();
		workList.add(rootPath);
		while (! workList.isEmpty()) {

			File path = workList.get(workList.size() - 1);
			if (path.isFile()) {
				path.delete();
				workList.remove(workList.size() - 1);
			} else if (path.isDirectory()) {
				if (path.list().length == 0) {
					path.delete();
					workList.remove(workList.size() - 1);
				} else {
					workList.addAll(Arrays.asList(path.listFiles()));
				}
			} else {
				throw new IllegalStateException();
			}
		}
	}

	private static void copy(final File from, final File to) throws IOException {

		List<File> copyList = Lists.newArrayList();

		List<File> fromList = Lists.newArrayList();
		fromList.add(from);
		while (! fromList.isEmpty()) {

			File path = fromList.remove(0);
			if (path.isFile()) {
				copyList.add(path);

			} else if (path.isDirectory()) {
				fromList.addAll(Arrays.asList(path.listFiles()));

			} else {
				throw new IllegalStateException();
			}
		}

		for (final File file : copyList) {
			Files.copy(file, relativeFile(from, to, file));
		}
	}

	private static File relativeFile(final File oldRoot, final File newRoot, final File file) {
		Preconditions.checkArgument(file.getAbsolutePath().startsWith(oldRoot.getAbsolutePath()));

		List<String> pathElements = Lists.newArrayList();
		File parent				  = file;
		while (! file.getParentFile().equals(parent)) {
			pathElements.add(file.getParentFile().getName());
			parent = file.getParentFile();
		}

		return new File(Joiner.on(File.separator).join(newRoot.getName(), pathElements));
	}
}
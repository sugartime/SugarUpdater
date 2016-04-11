package org.shineupdate;

import java.io.File;
import java.io.IOException;

import org.appkit.concurrent.Report;
import org.appkit.concurrent.ReportQueue;
import org.eclipse.swt.widgets.Display;
import org.shineupdate.UpdateWindow.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Updater implements Runnable {

	private static final Logger L = LoggerFactory.getLogger(Updater.class);

	public enum Status {FINISHED;
	}

	private final ReportQueue queue;
	private final UpdateWindow updateWindow;
	private final Downloader downloader;
	private final ReportQueue funnel;
	private final boolean showNotifications;

	public Updater(final boolean showNotifications) {
		this.showNotifications = showNotifications;
		this.queue		    = ReportQueue.create();
		this.updateWindow = UpdateWindow.create();
		this.downloader     = Downloader.create();

		/* funnel the two queues */
		this.funnel =
			ReportQueue.funnel(ShineUpdate.instance().executor, updateWindow.getReports(), downloader.getReports());
	}

	public ReportQueue getReports() {
		return this.queue;
	}

	@Override
	public void run() {
		try {
			this.check();
		} catch (final InterruptedException e) {
			L.error(e.getMessage(), e);
		} catch (final RuntimeException e) {
			L.error(e.getMessage(), e);
		} finally {
			this.queue.report(Status.FINISHED);
			Display.getDefault().wake();
		}
	}

	private void showError() throws InterruptedException {
		if (this.showNotifications) {
			updateWindow.showState(State.ERROR);
			this.funnel.take();
			updateWindow.close();
		}
	}

	private void check() throws InterruptedException {

		Report r = null;

		/* notification: wait */
		if (showNotifications) {
			updateWindow.showState(State.WAIT);
		}

		/* start download of version-description */
		downloader.loadLatestVersion(ShineUpdate.instance().url);

		VersionDescription versionDesc = null;
		while (true) {
			L.debug("waiting for reports from Downloader/UpdateWindow");

			r = funnel.take();
			L.debug("got report: {}", r);
			if (r.type == UpdateWindow.Action.CANCEL) {
				downloader.stopDownload();
				return;

			} else if (r.type == Downloader.Status.ERROR) {
				showError();
				return;

			} else if (r.type == Downloader.Status.VERSION) {
				versionDesc = (VersionDescription) r.data.get(0);
				break;
			}
		}

		/* compare Versions and check system compatibility */
		boolean skipThis = ShineUpdate.instance().prefStore.get("skipversion_" + versionDesc.getVersion(), false);
		String currentVersion = ShineUpdate.instance().currentVersion;
		if (currentVersion.equals(versionDesc.getVersion()) || ! versionDesc.isCompatibleWithSystem()) {

			/* notification: no update */
			if (showNotifications) {
				updateWindow.showState(State.NO_UPDATES);
				this.funnel.take();
				updateWindow.close();
			}

		} else if (skipThis && !showNotifications) {

			/* skip version if checking invisibly */
			L.debug("not showing notification and version {} should be skipped -> exiting", versionDesc.getVersion());
			return;

		} else {

			/* notification: changelog */
			L.debug("showing Changelog-Window for new version: {}", versionDesc.getVersion());
			updateWindow.showState(State.CHANGELOG, versionDesc);

			L.debug("waiting for report from UpdateWindow");
			r = funnel.take();
			L.debug("got report from UpdateWindow: {}", r);

			if (r.type == UpdateWindow.Action.REMIND_ME_LATER) {
				L.debug("saving current time as last-reminder");
				ShineUpdate.instance().prefStore.store(
					"lastremind_" + versionDesc.getVersion(),
					System.currentTimeMillis());

			} else if (r.type == UpdateWindow.Action.SKIP) {
				L.debug("saving wish to skip version {}", versionDesc.getVersion());
				ShineUpdate.instance().prefStore.store("skipversion_" + versionDesc.getVersion(), true);

			} else if (r.type == UpdateWindow.Action.INSTALL) {

				/* notification: downloading */
				L.debug("showing Download-Window");
				updateWindow.showState(State.DOWNLOADING);

				/* start download of update */
				downloader.loadUpdate(versionDesc.getDownloadURL(), versionDesc.getDownloadSize());

				while (true) {
					L.debug("waiting for reports from Downloader/DownloadWindow");

					r = funnel.take();
					L.debug("got report: {}", r);
					if (r.type == UpdateWindow.Action.CANCEL) {
						downloader.stopDownload();
						return;

					} else if (r.type == Downloader.Status.ERROR) {
						showError();
						return;

					} else if (r.type == Downloader.Status.PROGRESS) {
						updateWindow.showState(State.DOWNLOADING, r.data.get(0), r.data.get(1));

					} else if (r.type == Downloader.Status.DOWNLOADED_FILE) {

						File downloadedFile = (File) r.data.get(0);

						/* checksum */
						String hash = FileHelper.getSHA1Hash(downloadedFile);
						L.debug("checking hash");
						L.debug("manifest-hash: {}", versionDesc.getDownloadSHA1Hash());
						L.debug("file-hash: {}", hash);
						if (!hash.equals(versionDesc.getDownloadSHA1Hash())) {
							L.error("hashes do not match!");
							showError();
							return;
						}

						/* notification: restart-screen */
						L.debug("showing install/restart-window");
						updateWindow.showState(State.RESTART);
						L.debug("waiting for action from install/restart-window");
						updateWindow.getReports().take();
						L.debug("closing install/restart-window");
						updateWindow.close();

						/* unzip */
						File extractedApplication = null;
					//	if (OSUtils.isWindows()) {
					//		extractedApplication = downloadedFile;
					//	} else if (OSUtils.isMac()) {
							L.debug("mac -> unzipping file");
							extractedApplication = FileHelper.unzipMacApplication(downloadedFile);
					//	}

						/* prepare command */
						File application = new File("test");
						String cmd		 = InstallHelper.prepareCommand(extractedApplication, application);
						L.debug("prepared InstallHelper command: '{}'", cmd);

						/* run shutdown-hook */
						L.debug("running ShutdownHook ");
						ShineUpdate.instance().shutdownHook.shutdownForUpdate();

						/* run install helper */
						try {
							L.debug("running install-helper");
							Runtime.getRuntime().exec(cmd);

							/* exit system */
							L.debug("exiting system");
							System.exit(0);
						} catch (final IOException e) {
							L.error(e.getMessage(), e);
							showError();
						}
					}
				}
			}
		}
	}
}
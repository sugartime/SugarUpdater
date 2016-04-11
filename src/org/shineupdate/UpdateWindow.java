package org.shineupdate;

import com.google.common.eventbus.Subscribe;

import java.util.Locale;

import org.appkit.concurrent.GUI;
import org.appkit.registry.Images;
import org.appkit.templating.Component;
import org.appkit.templating.Templating;
import org.appkit.templating.event.ButtonEvent;
import org.appkit.templating.event.EventContext;
import org.appkit.templating.event.EventContexts;
import org.appkit.util.Texts;
import org.appkit.widget.util.GridUtils;
import org.appkit.widget.util.SWTUtils;
import org.appkit.widget.util.ShellUtils;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UpdateWindow extends GUI<UpdateWindow.State> {

	//~ Enumerations ---------------------------------------------------------------------------------------------------

	public enum State {WAIT, NO_UPDATES, CHANGELOG, DOWNLOADING, RESTART, ERROR;
	}
	public enum Action {SKIP, INSTALL, REMIND_ME_LATER, CANCEL, OK, RESTART;
	}

	//~ Static fields/initializers -------------------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private static final Logger L							 = LoggerFactory.getLogger(UpdateWindow.class);
	private static final Point sizeInfo						 = new Point(420, 150);
	private static final Point sizeChangelog				 = new Point(590, 390);
	private static final Point sizeProgress					 = new Point(380, 128);

	//~ Instance fields ------------------------------------------------------------------------------------------------

	private final Texts texts;
	private Shell shell;
	private Component updateWindow;
	private Composite compMain;

	/* parts */
	private Component waitComp;
	private Component noUpdatesComp;
	private Component changelogComp;
	private Component downloadingComp;
	private Component restartComp;
	private Component errorComp;

	//~ Constructors ---------------------------------------------------------------------------------------------------

	private UpdateWindow() {
		this.texts = Texts.fromResources(Locale.ENGLISH);
	}

	//~ Methods --------------------------------------------------------------------------------------------------------

	private void initialize() {
		if (this.shell != null) {
			return;
		}

		this.shell = new Shell(SWT.TITLE);
		this.shell.setLayout(new FillLayout());

		Templating templating = Templating.fromResources();
		templating.addType(VersionDescriptionBrowser.class, "changelogtable");
		EventContext context  = EventContexts.forSendingTo(this);

		/* init window */
		this.updateWindow = templating.create("updatewindow", context, this.shell);

		/* Logo */
		Images.set(this.updateWindow.select("logo", Label.class), "images/icon64.png");

		/* init other components */
		this.compMain = this.updateWindow.select("main-composite", Composite.class);
		this.compMain.setLayout(new StackLayout());

		this.waitComp			 = templating.create("wait", context, compMain);
		this.noUpdatesComp		 = templating.create("noupdates", context, compMain);
		this.changelogComp		 = templating.create("changelog", context, compMain);
		this.downloadingComp     = templating.create("download", context, compMain);
		this.restartComp		 = templating.create("restart", context, compMain);
		this.errorComp			 = templating.create("error", context, compMain);

		/* translate components */
		Texts.translateComponent(this.waitComp, Locale.ENGLISH);
		Texts.translateComponent(this.noUpdatesComp, Locale.ENGLISH);
		Texts.translateComponent(this.changelogComp, Locale.ENGLISH);
		Texts.translateComponent(this.downloadingComp, Locale.ENGLISH);
		Texts.translateComponent(this.restartComp, Locale.ENGLISH);
		Texts.translateComponent(this.errorComp, Locale.ENGLISH);
	}

	public static UpdateWindow create() {
		return new UpdateWindow();
	}

	@Override
	public GUIState getGUIState(final State state) {
		switch (state) {
			case WAIT:
				return new StateWait();
			case NO_UPDATES:
				return new StateNoUpdates();
			case CHANGELOG:
				return new StateChangelog();
			case DOWNLOADING:
				return new StateDownloading();
			case RESTART:
				return new StateRestart();
			case ERROR:
				return new StateError();
			default:
				throw new IllegalStateException();
		}
	}

	@Subscribe
	public void buttonPressed(final ButtonEvent event) {
		switch (event.getOrigin()) {
			case "action_skip":
				this.queue.report(Action.SKIP);
				break;
			case "action_remindlater":
				this.queue.report(Action.REMIND_ME_LATER);
				break;
			case "action_install":
				this.queue.report(Action.INSTALL);
				break;
			case "action_abort":
				this.queue.report(Action.CANCEL);
				break;
			case "action_ok":
				this.queue.report(Action.OK);
				break;
			case "action_restart":
				this.queue.report(Action.RESTART);
				break;
		}
	}

	@Override
	protected void closeGUI() {
		if (shell != null) {
			shell.dispose();
		}
	}

	private static String formatBytes(final long bytes, final boolean si) {

		int unit = si ? 1000 : 1024;
		if (bytes < unit) {
			return bytes + " B";
		}

		int exp    = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "KMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		if (pre.equals("K")) {
			return String.format("%.0f %sB", bytes / Math.pow(unit, exp), pre);
		} else {
			return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
		}
	}

	private final void updateShell(final Composite topControl, final Button defButton, final Point newSize) {
		shell.setVisible(false);
		shell.setDefaultButton(defButton);
		((StackLayout) compMain.getLayout()).topControl = topControl;
		shell.setSize(newSize);
		shell.layout(true, true);
		SWTUtils.fixCocoaAlignments(shell);
		ShellUtils.moveToMonitorCenter(shell);
		shell.open();
	}

	//~ Inner Classes --------------------------------------------------------------------------------------------------

	private final class StateWait implements GUIState {
		@Override
		public void enter(final Object... data) {
			initialize();
			shell.setText(texts.get("title_updating", ShineUpdate.instance().applicationName));
			updateShell(waitComp.getComposite(), null, sizeProgress);
		}

		@Override
		public void update(final Object... data) {}
	}

	private final class StateNoUpdates implements GUIState {
		@Override
		public void enter(final Object... data) {
			initialize();

			/* Shell Title */
			shell.setText("");

			/* Texts */
			String applicationName = ShineUpdate.instance().applicationName;
			String currentVersion  = ShineUpdate.instance().currentVersion;
			noUpdatesComp.select("uptodate_longer", Label.class).setText(
				texts.get("noupdates_uptodate_longer", applicationName, currentVersion));

			/* Update and open Shell */
			updateShell(noUpdatesComp.getComposite(), noUpdatesComp.select("action_ok", Button.class), sizeInfo);
		}

		@Override
		public void update(final Object... data) {}
	}

	private final class StateChangelog implements GUIState {
		@Override
		public void enter(final Object... data) {
			initialize();

			/* Shell Title */
			shell.setText(texts.get("title_updateavailable"));

			/* Texts */
			VersionDescription latestVersionDesc = (VersionDescription) data[0];
			String applicationName				 = ShineUpdate.instance().applicationName;
			String currentVersion				 = ShineUpdate.instance().currentVersion;

			changelogComp.select("title", Label.class).setText(texts.get("changelog_title", applicationName));

			if (latestVersionDesc.isMandatory()) {
				GridUtils.show(changelogComp.select("question_mandatory", Label.class));
				GridUtils.hide(changelogComp.select("question", Label.class));
				changelogComp.select("question_mandatory", Label.class).setText(
					texts.get(
						"changelog_question_mandatory",
						applicationName,
						latestVersionDesc.getVersion(),
						currentVersion));
				changelogComp.select("action_skip", Button.class).setEnabled(false);
				changelogComp.select("action_remindlater", Button.class).setEnabled(false);

			} else {
				GridUtils.hide(changelogComp.select("question_mandatory", Label.class));
				GridUtils.show(changelogComp.select("question", Label.class));
				changelogComp.select("question", Label.class).setText(
					texts.get("changelog_question", applicationName, latestVersionDesc.getVersion(), currentVersion));
				changelogComp.select("action_skip", Button.class).setEnabled(true);
				changelogComp.select("action_remindlater", Button.class).setEnabled(true);

			}

			/* table */
			changelogComp.select(VersionDescriptionBrowser.class).setVersionDescription(latestVersionDesc);

			/* Update and open Shell */
			updateShell(
				changelogComp.getComposite(),
				changelogComp.select("action_install", Button.class),
				sizeChangelog);
		}

		@Override
		public void update(final Object... data) {}
	}

	private final class StateDownloading implements GUIState {
		@Override
		public void enter(final Object... data) {
			initialize();

			/* Shell Title */
			shell.setText(texts.get("title_updating", ShineUpdate.instance().applicationName));

			/* ProgressBar init */
			downloadingComp.select(ProgressBar.class).setMinimum(0);

			/* Update and open Shell */
			updateShell(downloadingComp.getComposite(), null, sizeProgress);
		}

		@Override
		public void update(final Object... data) {

			int total    = (Integer) data[0];
			int progress = (Integer) data[1];

			/* Texts: DownloadProgress */
			String downloadProgress =
				texts.get("download_progress", formatBytes(progress, true), formatBytes(total, true));
			downloadingComp.select("progress", Label.class).setText(downloadProgress);

			/* ProgressBar */
			if (downloadingComp.select(ProgressBar.class).getMaximum() != total) {
				downloadingComp.select(ProgressBar.class).setMaximum(total);
			}
			downloadingComp.select(ProgressBar.class).setSelection(progress);
		}
	}

	private final class StateRestart implements GUIState {
		@Override
		public void enter(final Object... data) {
			initialize();

			/* Shell Title */
			shell.setText(texts.get("title_updating", ShineUpdate.instance().applicationName));

			/* ProgressBar to 100% */
			restartComp.select(ProgressBar.class).setMaximum(100);
			restartComp.select(ProgressBar.class).setSelection(100);

			/* Update and open Shell */
			updateShell(restartComp.getComposite(), restartComp.select("action_restart", Button.class), sizeProgress);
		}

		@Override
		public void update(final Object... data) {}
	}

	private final class StateError implements GUIState {
		@Override
		public void enter(final Object... data) {
			initialize();

			/* Shell Title */
			shell.setText("");

			/* Update and open Shell */
			updateShell(errorComp.getComposite(), errorComp.select("action_ok", Button.class), sizeInfo);
		}

		@Override
		public void update(final Object... data) {}
	}
}
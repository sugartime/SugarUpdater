package org.shineupdate;

import com.google.common.base.Preconditions;


import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.appkit.concurrent.LoggingThreadFactory;
import org.appkit.preferences.PrefStore;

import org.eclipse.swt.widgets.Display;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShineUpdate {

	//~ Static fields/initializers -------------------------------------------------------------------------------------

	private static final Logger L	    = LoggerFactory.getLogger(ShineUpdate.class);
	private static ShineUpdate instance;

	//~ Instance fields ------------------------------------------------------------------------------------------------

	protected final PrefStore prefStore;
	protected final ShutdownHook shutdownHook;
	protected final String url;
	protected final String applicationName;
	protected final String applicationUID;
	protected final String currentVersion;
	protected final Executor executor;

	//~ Constructors ---------------------------------------------------------------------------------------------------

	private ShineUpdate(final String url, final String applicationName, final String applicationUID,
						final String currentVersion, final ShutdownHook shutdownHook) {
		this.prefStore				    = PrefStore.createJavaPrefStore(StaticConfig.PREF_NODE + "/" + applicationUID);
		this.executor				    = Executors.newCachedThreadPool(LoggingThreadFactory.create());

		this.url					    = url;
		this.applicationName		    = applicationName;
		this.applicationUID			    = applicationUID;
		this.currentVersion			    = currentVersion;
		this.shutdownHook			    = shutdownHook;

		L.debug(
			"initialized shine-update, running application (id/version): '{}'/'{}'",
			applicationUID,
			currentVersion);
	}

	//~ Methods --------------------------------------------------------------------------------------------------------

	public static void instantiate(final String url, final String applicationName, final String applicationUID,
								   final String currentVersion) {
		instantiate(url, applicationName, applicationUID, currentVersion, ShutdownHook.NO_OP);
	}

	public static void instantiate(final String url, final String applicationName, final String applicationUID,
								   final String currentVersion, final ShutdownHook hook) {
		Preconditions.checkState(instance == null, "already instantiated, call instance()");
		instance = new ShineUpdate(url, applicationName, applicationUID, currentVersion, hook);
	}

	public static ShineUpdate instance() {
		Preconditions.checkState(instance != null, "call instantiate() first");
		return instance;
	}

	public void runInBackground(final long period, final TimeUnit timeUnit) {}

	/**
	 * Performs a version check.
	 */
	public void check(final boolean showNotifications) {
		L.debug("starting check, {}showing notifications", showNotifications ? "" : "not ");

		Updater updater = new Updater(showNotifications);

		if (Display.getDefault().getThread() == Thread.currentThread()) {
			this.executor.execute(updater);

			L.debug("we are on the SWT-Thread, holding the event-loop here");

			Display display = Display.getCurrent();
			while (! display.isDisposed() && (updater.getReports().poll() == null)) {
				if (! display.readAndDispatch()) {
					display.sleep();
				}
			}
			L.debug("released SWT-EventLoop");

		} else {
			updater.run();
		}
	}

	//~ Inner Interfaces -----------------------------------------------------------------------------------------------

	public interface ShutdownHook {

		public static final ShutdownHook NO_OP =
			new ShutdownHook() {
				@Override
				public void shutdownForUpdate() {}
			};

		/** will be called before shutting the JVM down for an update */
		public void shutdownForUpdate();
	}
}
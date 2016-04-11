package org.shineupdate;

public final class Test {

	//~ Methods --------------------------------------------------------------------------------------------------------

	public static void main(final String args[]) {
		System.setProperty("org.slf4j.simplelogger.defaultlog", "debug");
		ShineUpdate.instantiate("https://dl.dropbox.com/u/168982/KassaRelease/latest", "Test App", "testXYZ", "1.7.2");
		ShineUpdate.instance().check(true);
		System.exit(0);
	}
}
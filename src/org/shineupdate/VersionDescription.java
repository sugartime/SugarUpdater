package org.shineupdate;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;

import org.appkit.osdependant.OSUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VersionDescription {

	//~ Static fields/initializers -------------------------------------------------------------------------------------

	private static final Logger L = LoggerFactory.getLogger(Downloader.class);

	//~ Instance fields ------------------------------------------------------------------------------------------------

	private boolean isMandatory	= false;
	private String version;
	private String foreWord;
	private ImmutableList<Section> sections;
	private String downloadURL;
	private String downloadSHA1;
	private int downloadSize;

	//~ Constructors ---------------------------------------------------------------------------------------------------

	private VersionDescription() {}

	//~ Methods --------------------------------------------------------------------------------------------------------

	public static VersionDescription parse(final String description) {

		VersionDescription versionDesc	    = new VersionDescription();

		L.debug("parsing version-description");

		/* replace window-style line-endings by sane ones */
		String desc			  = description.replace("\\r\\n", "\n");

		/* split into section */
		List<String> sections = Lists.newArrayList(Splitter.on("\n\n").split(desc));
		Preconditions.checkState(sections.size() >= 2, "Need at least two sections (info & foreword");

		/* platform */
		String platform = OSUtils.getPlatform().toString().toLowerCase();
		L.debug("running on platform '{}'", platform);

		/* section 0: info & download */
		for (final String infoLine : Splitter.on("\n").split(sections.get(0))) {
			Preconditions.checkArgument(infoLine.contains(":"), "Info must consist of key-value pairs");

			String key   = infoLine.substring(0, infoLine.indexOf(':')).trim().toLowerCase();
			String value = infoLine.substring(infoLine.indexOf(':') + 1).trim();

			if (key.equals("version")) {
				versionDesc.version = value;
			} else if (key.equals("mandatory")) {
				versionDesc.isMandatory = true;
			} else if (key.equals(platform)) {
				versionDesc.downloadURL = value;
			} else if (key.equals(platform + "_sha1")) {
				versionDesc.downloadSHA1 = value;
			} else if (key.equals(platform + "_size")) {
				versionDesc.downloadSize = Integer.valueOf(value);
			} else {
				L.debug("ignored property '{}' -> '{}'", key, value);
			}
		}
		Preconditions.checkNotNull(versionDesc.version, "[version-description] version not found");

		/* section 1: foreword */
		versionDesc.foreWord = sections.get(1);

		/* additional sections */
		ImmutableList.Builder<Section> lb = ImmutableList.builder();
		for (int i = 2; i < sections.size(); i++) {
			lb.add(Section.parse(sections.get(i)));
		}
		versionDesc.sections = lb.build();

		return versionDesc;
	}

	public String getVersion() {
		return this.version;
	}

	public boolean isMandatory() {
		return this.isMandatory;
	}

	public boolean isCompatibleWithSystem() {
		return this.downloadURL != null;
	}

	public String getDownloadURL() {
		return this.downloadURL;
	}

	public String getDownloadSHA1Hash() {
		return this.downloadSHA1;
	}

	public int getDownloadSize() {
		return this.downloadSize;
	}

	public String getForeWord() {
		return this.foreWord;
	}

	public ImmutableList<Section> getSections() {
		return this.sections;
	}

	//~ Inner Classes --------------------------------------------------------------------------------------------------

	public static class Section {

		private String name;
		private String imageURL;
		private ImmutableList<String> bulletItems;

		private Section() {}

		public static Section parse(final String sectionString) {

			Section section = new Section();

			String line1 = sectionString.substring(0, sectionString.indexOf("\n"));
			Preconditions.checkArgument(line1.contains(":"), "section's first line must be '<title>:<optional-picture-url>'");

			/* name */
			section.name = line1.substring(0, line1.indexOf(":"));
			section.imageURL = line1.substring(line1.indexOf(":")+1).trim();
			if (section.imageURL.isEmpty()) {
				section.imageURL = null;
			}

			/* sections */
			String str						 = sectionString.substring(sectionString.indexOf("\n"));
			ImmutableList.Builder<String> lb = ImmutableList.builder();
			lb.addAll(Splitter.on("\n- ").trimResults().omitEmptyStrings().split(str));
			section.bulletItems = lb.build();

			return section;
		}

		public String getName() {
			return name;
		}

		public String getImageURL() {
			return imageURL;
		}

		public ImmutableList<String> getContent() {
			return bulletItems;
		}
	}
}
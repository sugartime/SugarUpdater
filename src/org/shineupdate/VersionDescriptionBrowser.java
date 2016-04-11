package org.shineupdate;

import java.io.InputStream;

import org.appkit.templating.Options;
import org.appkit.templating.event.EventContext;
import org.appkit.util.ResourceStringSupplier;
import org.appkit.widget.util.BrowserWidget;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.shineupdate.VersionDescription.Section;

import com.google.common.collect.ImmutableList;

public final class VersionDescriptionBrowser extends BrowserWidget {

	private VersionDescription description;

	public VersionDescriptionBrowser(final EventContext context, final Composite parent, final String name, final Options options) {
		super(parent, SWT.BORDER);
	}

	public void setVersionDescription(final VersionDescription description) {
		this.description = description;
		this.reloadWidget();
	}

	@Override
	public ImmutableList<String> getStyleSheets() {
		return ImmutableList.of(ResourceStringSupplier.instance().get("changelogbrowser.css"));
	}

	@Override
	public ImmutableList<String> getJavaScripts() {
		return ImmutableList.of();
	}

	@Override
	public String getBody() {
		StringBuilder sb = new StringBuilder();
		for (Section section : this.description.getSections()) {
			sb.append("<table>");
			sb.append("<tr><th colspan=2 class=\"header\">");
			sb.append(section.getName());
			sb.append("</th></tr>");

			if (section.getImageURL() != null) {
				sb.append("<tr><td class=\"image\">");

				sb.append("<img src=\"");
				sb.append(section.getImageURL());
				sb.append("\"/>");

				sb.append("</td><td class=\"contents\">");
			} else {
				sb.append("<tr><td colspan=2 class=\"contents\">");
			}

			if (section.getContent().size() == 1) {
				sb.append(section.getContent().get(0));
			} else {
				sb.append("<ul>");
				for (String bulletItem : section.getContent()) {
					sb.append("<li>");
					sb.append(bulletItem);
					sb.append("</li>");
				}
				sb.append("</ul>");
			}

			sb.append("</td></tr>");
			sb.append("</table>");
		}

		return sb.toString();
	}

	@Override
	public InputStream getImage(final String image) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object callback(final String jsFnName, final Object[] args) {
		return null;
	}

}

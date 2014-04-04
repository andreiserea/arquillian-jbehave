package org.jboss.arquillian.jbehave;

import java.util.HashMap;

public class ExtendedState {

	public static final String CRR_STEP = "step.current";
	public static final String STORY_PATHS = "story.paths";
	
	public static HashMap<String, Object> properties;
	
	public static void setProperty(String property, Object value) {
		if (properties == null) {
			properties = new HashMap<>();
		}
		properties.put(property, value);
	}
	
	public static Object getProperty(String property) {
		if (properties != null) {
			return properties.get(property);
		}
		return null;
	}
}

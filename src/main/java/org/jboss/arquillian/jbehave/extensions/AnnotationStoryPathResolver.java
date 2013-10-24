package org.jboss.arquillian.jbehave.extensions;

import org.jbehave.core.Embeddable;
import org.jboss.arquillian.jbehave.extensions.annotations.StoryFile;


/**
 * Finds a story file based on the @StoryFile annotation put on the {@link Embeddable} class
 * @author Andrei Serea
 *
 */
public class AnnotationStoryPathResolver extends JBossUnderscoredCamelCaseResolver {

	public AnnotationStoryPathResolver() {
		this(JBossUnderscoredCamelCaseResolver.DEFAULT_EXTENSION, JBossUnderscoredCamelCaseResolver.DEFAULT_STORY_FOLDER);
	}
	
	public AnnotationStoryPathResolver(String defaultExtension, String defaultStoryFolder) {
		super(defaultExtension, defaultStoryFolder);
	}

	@Override
	protected String resolveName(Class<? extends Embeddable> embeddableClass) {
		StoryFile ann;
		if ((ann = embeddableClass.getAnnotation(StoryFile.class)) != null) {
			return ann.value();
		}
		return super.resolveName(embeddableClass);
	}
	
}
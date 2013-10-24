package org.jboss.arquillian.jbehave.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jbehave.core.Embeddable;
import org.jbehave.core.io.AbstractStoryPathResolver;

/**
 * Exactly like the UnderscoredCamelCaseResolver only that it searches for the
 * story inside a given folder rather than in the same folder as the class.
 * @author Andrei Serea
 *
 */
public class JBossUnderscoredCamelCaseResolver extends AbstractStoryPathResolver {

	public static final String NUMBERS_AS_LOWER_CASE_LETTERS_PATTERN = "(([A-Z]{2,})|([A-Z].*?))([A-Z]|\\z)";
	public static final String NUMBERS_AS_UPPER_CASE_LETTERS_PATTERN = "([A-Z0-9].*?)([A-Z0-9]|\\z)";
	public static final String DEFAULT_EXTENSION = ".story";
	public static final String DEFAULT_STORY_FOLDER = "stories";
	private static final String UNDERSCORE = "_";
	private final String resolutionPattern;
	private final Locale locale;
	private final List<String> wordsToRemove = new ArrayList<>();
	protected final String storyFolder;
	protected final String extension;

	public JBossUnderscoredCamelCaseResolver() {
		this(JBossUnderscoredCamelCaseResolver.DEFAULT_EXTENSION,
				JBossUnderscoredCamelCaseResolver.DEFAULT_STORY_FOLDER);
	}

	public JBossUnderscoredCamelCaseResolver(final String storyFolder) {
		this(JBossUnderscoredCamelCaseResolver.DEFAULT_EXTENSION, storyFolder);
	}

	public JBossUnderscoredCamelCaseResolver(final String extension, final String storyFolder) {
		this(extension, storyFolder, JBossUnderscoredCamelCaseResolver.NUMBERS_AS_LOWER_CASE_LETTERS_PATTERN);

	}

	public JBossUnderscoredCamelCaseResolver(final String extension, final String storyFolder,
			final String resolutionPattern) {
		this(extension, storyFolder, resolutionPattern, Locale.getDefault());
	}

	public JBossUnderscoredCamelCaseResolver(final String extension, final String storyFolder,
			final String resolutionPattern, final Locale locale) {
		super(extension);
		this.extension = extension;
		this.storyFolder = storyFolder;
		this.resolutionPattern = resolutionPattern;
		this.locale = locale;
	}

	@Override
	protected String resolveName(final Class<? extends Embeddable> embeddableClass) {
		String simpleName = embeddableClass.getSimpleName();
		for (final String wordToRemove : this.wordsToRemove) {
			simpleName = simpleName.replace(wordToRemove, "");
		}
		final Matcher matcher = Pattern.compile(this.resolutionPattern).matcher(simpleName);
		int startAt = 0;
		final StringBuilder builder = new StringBuilder();
		while (matcher.find(startAt)) {
			builder.append(matcher.group(1).toLowerCase(this.locale));
			builder.append(JBossUnderscoredCamelCaseResolver.UNDERSCORE);
			startAt = matcher.start(4);
		}
		return builder.substring(0, builder.length() - 1);
	}

	public JBossUnderscoredCamelCaseResolver removeFromClassName(final String... wordsToRemove) {
		Collections.addAll(this.wordsToRemove, wordsToRemove);
		return this;
	}

	@Override
	public String resolve(final Class<? extends Embeddable> embeddableClass) {
		return this.storyFolder + "/" + this.resolveName(embeddableClass) + this.extension;
	}
}

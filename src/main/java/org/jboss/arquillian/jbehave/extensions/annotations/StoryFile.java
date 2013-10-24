package org.jboss.arquillian.jbehave.extensions.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.jbehave.core.Embeddable;

/**
 * Designed to be used with {@link Embeddable} implementations, it specifies the
 * name of the story file to which the class is binded
 * 
 * @author Andrei Serea
 * 
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface StoryFile {

	public String value();
}

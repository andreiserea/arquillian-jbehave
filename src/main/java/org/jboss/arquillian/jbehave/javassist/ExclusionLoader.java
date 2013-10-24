package org.jboss.arquillian.jbehave.javassist;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javassist.ClassPool;
import javassist.Loader;

/**
 * A class loader that serves classes from a javassist class pool
 * @author Andrei Serea
 *
 */
public class ExclusionLoader extends Loader {

	private Set<String> classesAllowedToLoad = new HashSet<>();
	private ClassPool classPool;

	public ExclusionLoader(ClassLoader parent, ClassPool cp, String... classesAllowedToLoad) {
		super(parent, cp);
		this.classPool = cp;
		this.classesAllowedToLoad.addAll(Arrays.asList(classesAllowedToLoad));
	}

	@Override
	protected Class loadClass(String name, boolean resolve) throws ClassFormatError, ClassNotFoundException {
		name = name.intern();
		synchronized (name) {
			Class c = findLoadedClass(name);
			if (c == null)
				c = loadClassByDelegation(name);

			if (c == null && this.classesAllowedToLoad.contains(name))
				c = findClass(name);

			if (c == null)
				c = delegateToParent(name);

			if (resolve)
				resolveClass(c);

			return c;
		}
	}
	
	@Override
    public URL getResource(String name) {
		String prepName = prepClassName(name);
		if (this.classesAllowedToLoad.contains(prepName)) {
			return this.classPool.find(prepName);
		} else {
			return super.getResource(name);
		}
    }
	
	private static String prepClassName(String name) {
		return name.replace("/", ".").replace(".class", "");
	}
}

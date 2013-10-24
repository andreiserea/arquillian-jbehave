package org.jboss.arquillian.jbehave.javassist;

import java.io.IOException;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;

/**
 * Utility class that creates new classes at runtime based on the methods and
 * fields of other classes provided as arguments
 * 
 * @author Andrei Serea
 * 
 */
public class CopyClass {

	public static <T> Class<T> copy(Class<T> source) {
		try {
			CtClass ctDestination = ClassPool.getDefault().makeClass(source.getName() + "Copycat");
			CtClass ctSource = ClassPool.getDefault().get(source.getName());
			for (CtField f : ctSource.getDeclaredFields()) {
				CtClass fieldTypeClass = ClassPool.getDefault().get(f.getType().getName());
				CtField ctField = new CtField(fieldTypeClass, f.getName(), ctDestination);
				ctDestination.addField(ctField);
			}
			// copy methods
			for (CtMethod m : ctSource.getDeclaredMethods()) {
				CtMethod newm = CtNewMethod.copy(m, m.getName(), ctDestination, null);
				ctDestination.addMethod(newm);
			}
			return ctDestination.toClass();
		} catch (NotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (CannotCompileException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static void copy(CtClass ctSource, CtClass ctDestination) {
		try {
			// copy fields
			ConstPool cp = ctDestination.getClassFile().getConstPool();
			for (CtField f : ctSource.getDeclaredFields()) {
				CtClass fieldTypeClass = ClassPool.getDefault().get(f.getType().getName());
				CtField ctField = new CtField(fieldTypeClass, f.getName(), ctDestination);
				ctDestination.addField(ctField);
			}
			// copy methods
			for (CtMethod m : ctSource.getDeclaredMethods()) {
				//copy the method prefixing it with the source class name
				CtMethod newm = CtNewMethod.copy(m, /*ctSource.getSimpleName() + "_" + */m.getName(), ctDestination, null);
				// with annotations
				AnnotationsAttribute invAnn = (AnnotationsAttribute) m.getMethodInfo().getAttribute(
						AnnotationsAttribute.invisibleTag);
				AnnotationsAttribute visAnn = (AnnotationsAttribute) m.getMethodInfo().getAttribute(
						AnnotationsAttribute.visibleTag);
				if (invAnn != null) {
					newm.getMethodInfo().addAttribute(invAnn.copy(cp, null));
				}
				if (visAnn != null) {
					newm.getMethodInfo().addAttribute(visAnn.copy(cp, null));
				}
				ctDestination.addMethod(newm);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static <T> Class<T> mergeClassesToNewClassWithBase(Class<T> base, Class<?>... toMerge) {
		CtClass ctBase;
		if (base.getName().endsWith("CopycatMerge")) {
			return base;
		}
		try {
			ctBase = ClassPool.getDefault().get(base.getName());
			CtClass ctDestination = ClassPool.getDefault().makeClass(base.getName() + "CopycatMerge", ctBase);
			ConstPool cp = ctDestination.getClassFile().getConstPool();
			for (Class<?> tClass : toMerge) {
				copy(ClassPool.getDefault().get(tClass.getName()), ctDestination);
			}
			// copy annotations from class level too
			AnnotationsAttribute invAnn = (AnnotationsAttribute) ctBase.getClassFile().getAttribute(
					AnnotationsAttribute.invisibleTag);
			AnnotationsAttribute visAnn = (AnnotationsAttribute) ctBase.getClassFile().getAttribute(
					AnnotationsAttribute.visibleTag);
			if (invAnn != null) {
				ctDestination.getClassFile().addAttribute(invAnn.copy(cp, null));
			}
			if (visAnn != null) {
				ctDestination.getClassFile().addAttribute(visAnn.copy(cp, null));
			}
			ctDestination.writeFile("__temp");
			ClassPool.getDefault().insertClassPath("__temp");
			return ctDestination.toClass();
		} catch (NotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (CannotCompileException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static <T> Class<T> mergeClassesToNewClass(Class<?>... toMerge) {
		try {
			CtClass ctDestination = ClassPool.getDefault().makeClass("CopycatMerge");
			for (Class<?> tClass : toMerge) {
				copy(ClassPool.getDefault().get(tClass.getName()), ctDestination);
			}
			return ctDestination.toClass();
		} catch (NotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (CannotCompileException e) {
			e.printStackTrace();
			return null;
		}
	}
}

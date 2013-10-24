package org.jboss.arquillian.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.arquillian.container.test.impl.RemoteExtensionLoader;
import org.jboss.arquillian.core.impl.loadable.LoadableExtensionLoader;
import org.jboss.arquillian.core.spi.ExtensionLoader;

/**
 * Arquillian utility class that provides means to determine whether a piece of test code is running on the server or locally
 * @author Andrei Serea
 *
 */
public final class ArquillianUtils {
		private static final String SERVICES = "META-INF/services";
	
	   public static boolean isRunningRemote() 
	   {
		   
		   Set<Class<? extends ExtensionLoader>> providers = load(ExtensionLoader.class, Thread.currentThread().getContextClassLoader());
		      if(providers.size() == 0)
		      {
		    	  providers = load(ExtensionLoader.class, LoadableExtensionLoader.class.getClassLoader());
		      }
	      if (providers.contains(RemoteExtensionLoader.class)) {
	    	  return true;
	      }
	      return false;
	   }
	   
	   private static <T> Set<Class<? extends T>> load(Class<T> serviceClass, ClassLoader loader) 
	   {
	      String serviceFile = SERVICES + "/" + serviceClass.getName();

	      LinkedHashSet<Class<? extends T>> providers = new LinkedHashSet<Class<? extends T>>();      
	      LinkedHashSet<Class<? extends T>> vetoedProviders = new LinkedHashSet<Class<? extends T>>();      
	      
	      try
	      {
	         Enumeration<URL> enumeration = loader.getResources(serviceFile);
	         while (enumeration.hasMoreElements())
	         {
	            final URL url = enumeration.nextElement();
	            final InputStream is = url.openStream();
	            BufferedReader reader = null;
	            
	            try
	            {
	               reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
	               String line = reader.readLine();
	               while (null != line)
	               {
	                  line = skipCommentAndTrim(line);
	   
	                  if (line.length() > 0)
	                  {
	                     try
	                     {
	                        boolean mustBeVetoed = line.startsWith("!");
	                        if (mustBeVetoed)
	                        {
	                           line = line.substring(1);
	                        }
	                        
	                        Class<? extends T> provider = loader.loadClass(line).asSubclass(serviceClass);
	                    
	                        if (mustBeVetoed) {
	                           vetoedProviders.add(provider);
	                        }
	                        
	                        if (vetoedProviders.contains(provider)) {
	                           providers.remove(provider);
	                        } else {
	                           providers.add(provider);
	                        }
	                     }
	                     catch (ClassCastException e)
	                     {
	                        throw new IllegalStateException("Service " + line + " does not implement expected type "
	                              + serviceClass.getName());
	                     }
	                  }
	                  line = reader.readLine();
	               }
	            }
	            finally
	            {
	               if (reader != null) 
	               {
	                  reader.close();
	               }
	            }
	         }
	      }
	      catch (Exception e)
	      {
	         throw new RuntimeException("Could not load services for " + serviceClass.getName(), e);
	      }
	      return providers;
	   }

	   
	   private static String skipCommentAndTrim(String line)
	   {
	      final int comment = line.indexOf('#');
	      if (comment > -1)
	      {
	         line = line.substring(0, comment);
	      }
	  
	      line = line.trim();
	      return line;
	   }
}

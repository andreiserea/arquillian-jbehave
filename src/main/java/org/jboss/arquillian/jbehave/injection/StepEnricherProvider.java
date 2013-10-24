/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.jbehave.injection;

import java.util.Collection;

import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.test.spi.TestEnricher;
import org.jboss.arquillian.test.spi.event.suite.Before;

/**
 * An observer for the {@link Before} event.
 * Used to obtain the {@link ServiceLoader} to eventually
 * access the {@link TestEnricher}s available to Arquillian.
 * 
 * @author Vineet Reynolds
 *
 */
public class StepEnricherProvider
{
   @Inject
   private Instance<ServiceLoader> serviceLoader;

   private static final ThreadLocal<Collection<TestEnricher>> enrichers = new ThreadLocal<Collection<TestEnricher>>();

   /**
    * Observe the {@link Before} event to obtain references to the {@link TestEnricher} instances.
    * Once the enrichers have been obtained, they're stored in a ThreadLocal instance for future reference.
    * @param event The event to observe
    * @throws Exception The exception thrown by the observer on failure.
    */
   public void enrich(@Observes Before event) throws Exception
   {
      Collection<TestEnricher> testEnrichers = serviceLoader.get().all(TestEnricher.class);
      enrichers.set(testEnrichers);
   }

   public static Collection<TestEnricher> getEnrichers()
   {
      return enrichers.get();
   }

}

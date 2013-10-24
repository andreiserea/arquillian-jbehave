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

import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.List;

import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.steps.InstanceStepsFactory;
import org.jboss.arquillian.test.spi.TestEnricher;

/**
 * A Steps Factory that uses the Arquillian test enrichers to
 * inject the provided step instances with the necessary dependencies.  
 * 
 * @author Vineet Reynolds
 *
 */
public class ArquillianInstanceStepsFactory extends InstanceStepsFactory
{

   public ArquillianInstanceStepsFactory(Configuration configuration, List<Object> stepsInstances)
   {
      super(configuration, stepsInstances);
   }

   public ArquillianInstanceStepsFactory(Configuration configuration, Object... stepsInstances)
   {
      super(configuration, asList(stepsInstances));
   }
   
   @Override
   public Object createInstanceOfType(Class<?> type)
   {
      Object instance = super.createInstanceOfType(type);
      Collection<TestEnricher> stepEnrichers = StepEnricherProvider.getEnrichers();
      if (stepEnrichers != null) {
          for (TestEnricher stepEnricher : stepEnrichers)
          {
             stepEnricher.enrich(instance);
          }
      }
      return instance;
   }
   
   @Override
   public List<Class<?>> stepsTypes() {
	   return super.stepsTypes();
   }

}

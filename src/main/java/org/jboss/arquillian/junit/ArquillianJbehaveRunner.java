package org.jboss.arquillian.junit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javassist.ClassPool;

import org.jbehave.core.ConfigurableEmbedder;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.embedder.Embedder;
import org.jbehave.core.embedder.EmbedderControls;
import org.jbehave.core.embedder.StoryRunner;
import org.jbehave.core.failures.UUIDExceptionWrapper;
import org.jbehave.core.io.StoryPathResolver;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.junit.JUnitStory;
import org.jbehave.core.model.Story;
import org.jbehave.core.reporters.StoryReporterBuilder;
import org.jbehave.core.steps.AbstractStepResult;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.InjectableStepsFactory;
import org.jbehave.core.steps.NullStepMonitor;
import org.jbehave.core.steps.Step;
import org.jbehave.core.steps.StepCreator.ParameterisedStep;
import org.jbehave.core.steps.StepCreator.PendingStep;
import org.jbehave.core.steps.StepCreator.ScenarioStep;
import org.jbehave.core.steps.StepExecutor;
import org.jbehave.core.steps.StepLifecycleObserver;
import org.jbehave.core.steps.StepMonitor;
import org.jbehave.core.steps.StepResult;
import org.jboss.arquillian.jbehave.injection.ArquillianInstanceStepsFactory;
import org.jboss.arquillian.jbehave.javassist.CopyClass;
import org.jboss.arquillian.jbehave.javassist.ExclusionLoader;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.jboss.arquillian.utils.ArquillianUtils;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import de.codecentric.jbehave.junit.monitoring.JUnitDescriptionGenerator;

/**
 * Arquillian runner that is capable of running a ConfigurableEmbedder inside the container.
 * In other words, it runs the scenarios inside the container, permitting dependency injection in the steps classes!
 * @author Andrei Serea
 *
 */
public class ArquillianJbehaveRunner extends Arquillian {

	private List<Description> storyDescriptions;
	private Embedder configuredEmbedder;
	private List<String> storyPaths;
	private Configuration configuration;
	private int numberOfTestCases;
	private Description rootDescription;
	List<CandidateSteps> candidateSteps;
	private ConfigurableEmbedder configurableEmbedder;
	private static Object testClassInstance = null;
	private static StoryRunner.State crrState = null;
	private static Step previousStep = null;
	private static boolean executedLastStepOfScenario = false;
	private static boolean isRunningRemote = false;
	private boolean shouldExecuteCrrStep = false;
	private boolean remoteExecutedStep = false;

	public ArquillianJbehaveRunner(Class<? extends ConfigurableEmbedder> testClass) throws Throwable {
		super(CopyClass.mergeClassesToNewClassWithBase(testClass, ((ArquillianInstanceStepsFactory) testClass.newInstance()
				.stepsFactory()).stepsTypes().toArray(new Class<?>[] {})));
		String testClassName = this.getTestClass().getJavaClass().getName();
		Thread.currentThread().setContextClassLoader(
				new ExclusionLoader(Thread.currentThread().getContextClassLoader(), ClassPool.getDefault(),
						testClassName));
		ArquillianJbehaveRunner.testClassInstance = this.getTestClass().getJavaClass().newInstance();
		configurableEmbedder = testClass.newInstance();

		if (configurableEmbedder instanceof JUnitStories) {
			getStoryPathsFromJUnitStories(testClass);
		} else if (configurableEmbedder instanceof JUnitStory) {
			getStoryPathsFromJUnitStory();
		}

		configuration = configuredEmbedder.configuration();
		configuration.useStepLifecycleObserver(new JUnitStepLifecycleObserver());
		configuration.useStepExecutor(new ArquillianStepExecutor());

		StepMonitor originalStepMonitor = createCandidateStepsWithNoMonitor();
		storyDescriptions = buildDescriptionFromStories();
		createCandidateStepsWith(originalStepMonitor);

		initRootDescription();
		
		isRunningRemote = ArquillianUtils.isRunningRemote();
	}

	   @Override
	   public void run(final RunNotifier notifier)
	   {
		   super.run(notifier);
		   if (State.runnerCurrent() != null) { // don't do this again if RunListener.testRunFinished was called inside Arquillian.run()
			   State.runnerFinished();
	            try
	            {
	               if(State.isLastRunner())
	               {
	                  try
	                  {
	                     if(State.hasTestAdaptor())
	                     {
	                        TestRunnerAdaptor adaptor = State.getTestAdaptor();
	                        adaptor.afterSuite();
	                        adaptor.shutdown();
	                     }
	                  }
	                  finally
	                  {
	                     State.clean();
	                  }
	               }
	            }
	            catch (Exception e)
	            {
	               throw new RuntimeException("Could not run @AfterSuite", e);
	            }
		   }
	   }
	
	@Override
	public Description getDescription() {
		return rootDescription;
	}

	@Override
	public int testCount() {
		return numberOfTestCases;
	}

	/**
	 * Returns a {@link Statement}: Call {@link #runChild(Object, RunNotifier)}
	 * on each object returned by {@link #getChildren()} (subject to any imposed
	 * filter and sort)
	 */
	@Override
	protected Statement childrenInvoker(final RunNotifier notifier) {
		return new Statement() {
			@Override
			public void evaluate() {
				ArquillianJbehaveJUnitScenarioReporter junitReporter = new ArquillianJbehaveJUnitScenarioReporter(notifier, numberOfTestCases,
						rootDescription, configuration.keywords());
				// tell the reporter how to handle pending steps
				junitReporter.usePendingStepStrategy(configuration.pendingStepStrategy());

				addToStoryReporterFormats(junitReporter);

				try {
					configuredEmbedder.runStoriesAsPaths(storyPaths);
				} catch (Throwable e) {
					throw new RuntimeException(e);
				} finally {
					configuredEmbedder.generateCrossReference();
				}
			}
		};
	}

	public static EmbedderControls recommandedControls(Embedder embedder) {
		return embedder.embedderControls()
		// don't throw an exception on generating reports for failing stories
				.doIgnoreFailureInView(true)
				// don't throw an exception when a story failed
				.doIgnoreFailureInStories(true)
				// .doVerboseFailures(true)
				.useThreads(1);
	}

	private void createCandidateStepsWith(StepMonitor stepMonitor) {
		// reset step monitor and recreate candidate steps
		configuration.useStepMonitor(stepMonitor);
		getCandidateSteps();
		for (CandidateSteps step : candidateSteps) {
			step.configuration().useStepMonitor(stepMonitor);
		}
	}

	private StepMonitor createCandidateStepsWithNoMonitor() {
		StepMonitor usedStepMonitor = configuration.stepMonitor();
		createCandidateStepsWith(new NullStepMonitor());
		return usedStepMonitor;
	}

	private void getStoryPathsFromJUnitStory() {
		configuredEmbedder = configurableEmbedder.configuredEmbedder();
		StoryPathResolver resolver = configuredEmbedder.configuration().storyPathResolver();
		storyPaths = Arrays.asList(resolver.resolve(configurableEmbedder.getClass()));
	}

	@SuppressWarnings("unchecked")
	private void getStoryPathsFromJUnitStories(Class<? extends ConfigurableEmbedder> testClass)
			throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		configuredEmbedder = configurableEmbedder.configuredEmbedder();
		Method method = makeStoryPathsMethodPublic(testClass);
		storyPaths = ((List<String>) method.invoke((JUnitStories) configurableEmbedder, (Object[]) null));
	}

	private Method makeStoryPathsMethodPublic(Class<? extends ConfigurableEmbedder> testClass)
			throws NoSuchMethodException {
		Method method;
		try {
			method = testClass.getDeclaredMethod("storyPaths", (Class[]) null);
		} catch (NoSuchMethodException e) {
			method = testClass.getMethod("storyPaths", (Class[]) null);
		}
		method.setAccessible(true);
		return method;
	}

	private void getCandidateSteps() {
		// candidateSteps = configurableEmbedder.configuredEmbedder()
		// .stepsFactory().createCandidateSteps();
		InjectableStepsFactory stepsFactory = configurableEmbedder.stepsFactory();
		if (stepsFactory != null) {
			candidateSteps = stepsFactory.createCandidateSteps();
		} else {
			Embedder embedder = configurableEmbedder.configuredEmbedder();
			candidateSteps = embedder.candidateSteps();
			if (candidateSteps == null || candidateSteps.isEmpty()) {
				candidateSteps = embedder.stepsFactory().createCandidateSteps();
			}
		}
	}

	private void initRootDescription() {
		rootDescription = Description.createSuiteDescription(configurableEmbedder.getClass());
		rootDescription.getChildren().addAll(storyDescriptions);
	}

	private void addToStoryReporterFormats(ArquillianJbehaveJUnitScenarioReporter junitReporter) {
		StoryReporterBuilder storyReporterBuilder = configuration.storyReporterBuilder();
		StoryReporterBuilder.ProvidedFormat junitReportFormat = new StoryReporterBuilder.ProvidedFormat(junitReporter);
		storyReporterBuilder.withFormats(junitReportFormat);
	}

	private List<Description> buildDescriptionFromStories() {
		JUnitDescriptionGenerator descriptionGenerator = new JUnitDescriptionGenerator(candidateSteps, configuration);
		StoryRunner storyRunner = new StoryRunner();
		List<Description> storyDescriptions = new ArrayList<Description>();

		addSuite(storyDescriptions, "BeforeStories");
		addStories(storyDescriptions, storyRunner, descriptionGenerator);
		addSuite(storyDescriptions, "AfterStories");

		numberOfTestCases += descriptionGenerator.getTestCases();

		return storyDescriptions;
	}

	private void addStories(List<Description> storyDescriptions, StoryRunner storyRunner, JUnitDescriptionGenerator gen) {
		for (String storyPath : storyPaths) {
			Story parseStory = storyRunner.storyOfPath(configuration, storyPath);
			Description descr = gen.createDescriptionFrom(parseStory);
			storyDescriptions.add(descr);
		}
	}

	private void addSuite(List<Description> storyDescriptions, String name) {
		storyDescriptions.add(Description.createTestDescription(Object.class, name));
		numberOfTestCases++;
	}

	public class ArquillianStepExecutor implements StepExecutor {
		public StoryRunner.State invoke(final StoryRunner.State state, final Step step) {
			// update the crrState with the new storyrunner if necessary
			if (crrState != null) {
				crrState = copyState(crrState, state);
			}
			// nextState picks up from where the last step execution left off on
			// the server
			final StoryRunner.State nextState = crrState != null ? crrState : state;
			if (!shouldExecuteCrrStep) {
				executedLastStepOfScenario = false;
				return crrState = nextState;
			}
			executedLastStepOfScenario = true;
			final List<StoryRunner.State> result = new ArrayList<>();
			try {
				//report the beginning of the test to the storyreporter only if it is ran locally (otherwise it is done inside state.run(step) 
				if (!isRunningRemote) {
					if (step instanceof ParameterisedStep) {
						((ParameterisedStep) step).describeTo(state.storyRunner().storyReporter());
					}
				}
				Method initialMethod = step.method();
				final Method stepMethodClone = getTestClass().getJavaClass().getMethod(
						/*initialMethod.getDeclaringClass().getSimpleName() + "_" + */initialMethod.getName(),
						step.method().getParameterTypes());
				final TestResult jUnitResult = State.getTestAdaptor().test(new TestMethodExecutor() {
					public void invoke(Object... parameters) throws Throwable {
						result.add(nextState.run(step));
					}

					public Method getMethod() {
						return stepMethodClone;
					}

					public Object getInstance() {
						return testClassInstance;
					}
				});
				// 2 cases there: either this is ran locally and state.run
				// doesn't execute => result is empty
				// or it is ran remote so result contains the new state
				if (result.size() == 0) { // test ran locally
					// look at state type first
					if (nextState instanceof StoryRunner.FineSoFar) {
						// look at test result
						if (jUnitResult.getStatus() == TestResult.Status.PASSED) {
							AbstractStepResult.successful(step.stepAsString()).describeTo(
									state.storyRunner().storyReporter());
							crrState = nextState;
						} else if (jUnitResult.getStatus() == TestResult.Status.SKIPPED) {
							// this is a pending step
							StepResult stepResult = AbstractStepResult.pending(step.stepAsString());
							stepResult.describeTo(state.storyRunner().storyReporter());
							crrState = state.storyRunner().new SomethingHappened(stepResult.getFailure());
						} else if (jUnitResult.getStatus() == TestResult.Status.FAILED) {
							// failed, must return new state - something
							// happened
							UUIDExceptionWrapper exWrapper = new UUIDExceptionWrapper(step.stepAsString(),
									jUnitResult.getThrowable());
							AbstractStepResult.failed(step.stepAsString(), exWrapper).describeTo(
									state.storyRunner().storyReporter());
							crrState = state.storyRunner().new SomethingHappened(exWrapper);
						}
					} else if (nextState instanceof StoryRunner.SomethingHappened) {
						// should only be passed here
						assert jUnitResult.getStatus() == TestResult.Status.PASSED : "Test not passed although it was executed using SomethingHappened state!";
						AbstractStepResult.notPerformed(step.stepAsString()).describeTo(
								state.storyRunner().storyReporter());
						crrState = nextState; // this I think is useless but
												// better be safe than sorry
					}
				} else {
					crrState = result.get(0);
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
			return crrState;
		}
	}

	public class JUnitStepLifecycleObserver implements StepLifecycleObserver {

		public void beforeStepExecution(Step step) {
			shouldExecuteCrrStep = shouldInvoke(step);
			if (!shouldExecuteCrrStep) {
				return;
			}
			try {
				Method initialMethod = step.method();
				Method stepMethodClone = getTestClass().getJavaClass().getMethod(
						/*initialMethod.getDeclaringClass().getSimpleName() + "_" + */initialMethod.getName(),
						step.method().getParameterTypes());
				final FrameworkMethod method = new FrameworkMethod(stepMethodClone);
				ArquillianJbehaveRunner.this.withBefores(method, testClassInstance, new EmptyStatement()).evaluate();

			} catch (Throwable e) {
				e.printStackTrace();
			}
		}

		public void afterStepExecution(Step step) {
			if (!shouldExecuteCrrStep) {
				return;
			}
			try {
				Method initialMethod = step.method();
				Method stepMethodClone = getTestClass().getJavaClass().getMethod(
						/*initialMethod.getDeclaringClass().getSimpleName() + "_" + */initialMethod.getName(),
						step.method().getParameterTypes());
				final FrameworkMethod method = new FrameworkMethod(stepMethodClone);
				ArquillianJbehaveRunner.this.withAfters(method, testClassInstance, new EmptyStatement()).evaluate();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				// remember this step as the last step executed
				previousStep = step;
				remoteExecutedStep = true;
			}
		}
	}

	private static class EmptyStatement extends Statement {
		@Override
		public void evaluate() throws Throwable {
		}
	}

	private boolean shouldInvoke(Step step) {
		//prevent remote ArquillianJBehaveRunner from running more then one pending step at a time
		if (remoteExecutedStep && isRunningRemote) {
			return false;
		}
		//if step is pending, return false but test to see if it could be executed in case it wasn't pending
		//this is needed to keep the track of running progress (successfully executing other scenarios after previous one got pending steps)
		if (step instanceof PendingStep) {
			if (previousStep == null || ((ScenarioStep)step).follows((ScenarioStep)previousStep, storyPaths)) {
				//mark it as executed but return false to actually skip execution
				if (previousStep == null) {
					System.out.println("FIRST STEP FOUND!!!");
				}
				previousStep = step;
				//this prevents execution in remote of more then one step at a time
				remoteExecutedStep = true;
				//return false here, not only if step's method is null because a pending step may have a method annotated with @Pending
				return false;
			}
		}
		Method m = step.method();
		if (m == null) {
			return false;
		}
		Description testClassDescription = super.getDescription();
		for (Description d : testClassDescription.getChildren()) {
			if (d.getMethodName() != null && d.getMethodName().equals(m.getName())) {
				//if methods match, check if it is the right jbehave step, based on previous step (if not null)
				if (previousStep != null) {
					assert step instanceof ScenarioStep;
					return ((ScenarioStep)step).follows((ScenarioStep)previousStep, storyPaths);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Reset the crrState after each scenario
	 */
	public static void afterScenario() {
		// reset the state of jbehave runner only if the last step of the
		// scenario was executed
		if (executedLastStepOfScenario) {
			crrState = null;
		}
	}

	/**
	 * If the 2 states belong to different story runners then return a new state
	 * representing the old state but belonging to the new runner. Else the
	 * oldState is returned.
	 * 
	 * @param oldState
	 * @param newState
	 * @return
	 */
	private static StoryRunner.State copyState(StoryRunner.State oldState, StoryRunner.State newState) {
		if (newState.storyRunner() != oldState.storyRunner()) {
			if (oldState instanceof StoryRunner.FineSoFar) {
				return newState.storyRunner().new FineSoFar();
			} else {
				return newState.storyRunner().new SomethingHappened(
						((StoryRunner.SomethingHappened) oldState).scenarioFailure());
			}
		} else {
			return oldState;
		}
	}
}
package org.jboss.arquillian.junit;

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jbehave.core.ConfigurableEmbedder;
import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.embedder.Embedder;
import org.jbehave.core.embedder.NullEmbedderMonitor;
import org.jbehave.core.embedder.StoryRunner;
import org.jbehave.core.failures.BeforeOrAfterFailed;
import org.jbehave.core.failures.UUIDExceptionWrapper;
import org.jbehave.core.io.StoryPathResolver;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.junit.JUnitStory;
import org.jbehave.core.model.Story;
import org.jbehave.core.reporters.Format;
import org.jbehave.core.reporters.NullStoryReporter;
import org.jbehave.core.reporters.StoryReporter;
import org.jbehave.core.reporters.StoryReporterBuilder;
import org.jbehave.core.steps.AbstractStepResult;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.InjectableStepsFactory;
import org.jbehave.core.steps.NullStepMonitor;
import org.jbehave.core.steps.Step;
import org.jbehave.core.steps.StepCreator.BeforeOrAfterStep;
import org.jbehave.core.steps.StepCreator.ParameterisedStep;
import org.jbehave.core.steps.StepCreator.PendingStep;
import org.jbehave.core.steps.StepCreator.ScenarioStep;
import org.jbehave.core.steps.StepExecutor;
import org.jbehave.core.steps.StepIndex;
import org.jbehave.core.steps.StepLifecycleObserver;
import org.jbehave.core.steps.StepMonitor;
import org.jbehave.core.steps.StepResult;
import org.jboss.arquillian.jbehave.ExtendedState;
import org.jboss.arquillian.jbehave.container.JBehaveProgressNotifier;
import org.jboss.arquillian.jbehave.container.JBehaveProgressNotifierMXBean;
import org.jboss.arquillian.jbehave.injection.ArquillianInstanceStepsFactory;
import org.jboss.arquillian.jbehave.javassist.CopyClass;
import org.jboss.arquillian.test.spi.TestMethodExecutor;
import org.jboss.arquillian.test.spi.TestResult;
import org.jboss.arquillian.test.spi.TestRunnerAdaptor;
import org.jboss.arquillian.utils.ArquillianUtils;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import de.codecentric.jbehave.junit.monitoring.JUnitDescriptionGenerator;
import de.codecentric.jbehave.junit.monitoring.JUnitScenarioReporter;

/**
 * Arquillian runner that is capable of running a ConfigurableEmbedder inside the container. In other words, it runs the
 * scenarios inside the container, permitting dependency injection in the steps classes!
 * 
 * @author Andrei Serea
 * 
 */
public class ArquillianJbehaveRunner extends Arquillian {

	private List<Description> storyDescriptions;
	protected Embedder configuredEmbedder;
	protected static List<String> storyPaths;
	protected Configuration configuration;
	private int numberOfTestCases;
	private Description rootDescription;
	List<CandidateSteps> candidateSteps;
	private ConfigurableEmbedder configurableEmbedder;
	protected static Object testClassInstance = null;
	private static StoryRunner.State crrState = null;
	protected static boolean isRunningRemote = false;
	private boolean shouldExecuteCrrStep = false;
	private static Step remoteExecutedStep = null;
	private static boolean firstTime = true;
	/**
	 * Stores in the container the index of the next step to run
	 */
	private StepIndex stepToRun;
	

	public ArquillianJbehaveRunner(Class<? extends ConfigurableEmbedder> testClass) throws Throwable {
		super(CopyClass.mergeClassesToNewClassWithBase(testClass, ((ArquillianInstanceStepsFactory) testClass
				.newInstance().stepsFactory()).stepsTypes().toArray(new Class<?>[] {})));
		long startTime = System.currentTimeMillis();
		if (firstTime) {
			ArquillianJbehaveRunner.testClassInstance = this.getTestClass().getJavaClass().newInstance();
		}
		configurableEmbedder = testClass.newInstance();
		configuredEmbedder = configurableEmbedder.configuredEmbedder();

		if (firstTime) {
			if (configurableEmbedder instanceof JUnitStories) {
				getStoryPathsFromJUnitStories(testClass);
			} else if (configurableEmbedder instanceof JUnitStory) {
				getStoryPathsFromJUnitStory();
			}
		}
		// set the story paths properties in the extended state 
		ExtendedState.setProperty(ExtendedState.STORY_PATHS, storyPaths);

		configuration = configuredEmbedder.configuration();
		configuration.useStepLifecycleObserver(new JUnitStepLifecycleObserver());
		configuration.useStepExecutor(new ArquillianStepExecutor());

		StepMonitor originalStepMonitor = createCandidateStepsWithNoMonitor();
		storyDescriptions = buildDescriptionFromStories();
		createCandidateStepsWith(originalStepMonitor);

		initRootDescription(); // maybe declare as static?
		if (firstTime) {
			isRunningRemote = ArquillianUtils.isRunningRemote();
		}
		// don't print anything on the console inside the container
		if (isRunningRemote) {
			configuredEmbedder.useEmbedderMonitor(new NullEmbedderMonitor());
			configuration.storyReporterBuilder().formats().remove(Format.CONSOLE);
		}
//		// initialize the proxy to the JBehaveProgressNotifierMBean in the local JUnit process
//		if (!isRunningRemote) {
//			// TODO: change with info from arquillian.properties
//			JMXServiceURL u = new JMXServiceURL("service:jmx:remoting-jmx://localhost:9999");
//			JMXConnector c = JMXConnectorFactory.connect(u, new HashMap<String, Object>());
//			conn = c.getMBeanServerConnection();
		if (isRunningRemote) {
			MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
			this.stepToRun = JMX.newMXBeanProxy(mbs,
					new ObjectName(JBehaveProgressNotifier.JBEHAVE_PROGRESS_NOTIFIER_NAME),
					JBehaveProgressNotifierMXBean.class).getNextStep();
		}
		remoteExecutedStep = null;
		System.out.println("Init time: " + (System.currentTimeMillis() - startTime));
		firstTime = false;
	}

	@Override
	public void run(final RunNotifier notifier) {
		super.run(notifier);
		System.out.println("junit.ant value: " + System.getProperty("junit.ant"));
		if (System.getProperty("junit.ant") != null) {
			// when running from ant, testRunFinished notifier is not invoked
			// and arquillian won't shut things down after finishing
			State.runnerFinished();
			try {
				if (State.isLastRunner()) {
					try {
						if (State.hasTestAdaptor()) {
							TestRunnerAdaptor adaptor = State.getTestAdaptor();
							adaptor.afterSuite();
							adaptor.shutdown();
						}
					} finally {
						State.clean();
					}
				}
			} catch (Exception e) {
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
	 * Returns a {@link Statement}: Call {@link #runChild(Object, RunNotifier)} on each object returned by
	 * {@link #getChildren()} (subject to any imposed filter and sort)
	 */
	@Override
	protected Statement childrenInvoker(final RunNotifier notifier) {
		return new Statement() {
			@Override
			public void evaluate() {
				JUnitScenarioReporter junitReporter = new JUnitScenarioReporter(
						notifier, numberOfTestCases, rootDescription, configuration.keywords(), false);
				StateResetStoryReporter stateResetStoryReporter = new StateResetStoryReporter();
				// tell the reporter how to handle pending steps
				junitReporter.usePendingStepStrategy(configuration.pendingStepStrategy());
				configureStoryReporterFormats(junitReporter, stateResetStoryReporter);

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
		StoryPathResolver resolver = configuredEmbedder.configuration().storyPathResolver();
		storyPaths = Arrays.asList(resolver.resolve(configurableEmbedder.getClass()));
	}

	@SuppressWarnings("unchecked")
	private void getStoryPathsFromJUnitStories(Class<? extends ConfigurableEmbedder> testClass)
			throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
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

	protected void configureStoryReporterFormats(StoryReporter ... storyReporters) {
		StoryReporterBuilder storyReporterBuilder = configuration.storyReporterBuilder();
		List<StoryReporterBuilder.ProvidedFormat> formats = new ArrayList<>();
		for(StoryReporter storyReporter : storyReporters) {
			formats.add(new StoryReporterBuilder.ProvidedFormat(storyReporter));
		}
		storyReporterBuilder.withFormats(formats.toArray(new StoryReporterBuilder.ProvidedFormat[] {}));
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
			StoryRunner.State computedNextState = state;
			if (isRunningRemote) {
				if (!shouldExecuteCrrStep) {
					return state;
				}
				if (crrState != null) {
					// update the crrState with the new storyrunner if necessary
					crrState = copyState(crrState, state);
					// nextState picks up from where the last step execution
					// left off on the server
					computedNextState = crrState;
				}
			}
			final StoryRunner.State nextState = computedNextState;

			final List<StoryRunner.State> result = new ArrayList<>();
			ExtendedState.setProperty(ExtendedState.CRR_STEP, step);
			try {
				// report the beginning of the test to the storyreporter only if
				// it is ran locally (otherwise it is done inside
				// state.run(step)
				if (!isRunningRemote) {
					if (step instanceof ParameterisedStep) {
						((ParameterisedStep) step).describeTo(state.storyRunner().storyReporter());
					}
				}
				Method initialMethod = step.method();
				final Method stepMethodClone = getTestClass().getJavaClass().getMethod(
				/*
				 * initialMethod.getDeclaringClass().getSimpleName() + "_" +
				 */initialMethod.getDeclaringClass().getSimpleName() + "_" + initialMethod.getName(),
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
							StepResult stepResult = null;
							if (step instanceof BeforeOrAfterStep) {
								stepResult = AbstractStepResult.skipped();
							} else if (step instanceof ScenarioStep) {
								stepResult = AbstractStepResult.successful(step.stepAsString());
							}
							if (stepResult != null) {
								stepResult.describeTo(state.storyRunner().storyReporter());
							} else {
								System.err.println("step of unexpected type: " + step.getClass().getName());
							}

							crrState = nextState;
						} else if (jUnitResult.getStatus() == TestResult.Status.SKIPPED) {
							// this is a pending step
							StepResult stepResult = AbstractStepResult.pending(step.stepAsString());
							stepResult.describeTo(state.storyRunner().storyReporter());
							crrState = state.storyRunner().new SomethingHappened(stepResult.getFailure());
						} else if (jUnitResult.getStatus() == TestResult.Status.FAILED) {
							// failed, must return new state - something
							// happened
							UUIDExceptionWrapper exWrapper = null;
							if (step instanceof BeforeOrAfterStep) {
								exWrapper = new UUIDExceptionWrapper(new BeforeOrAfterFailed(
										((BeforeOrAfterStep) step).method(), jUnitResult.getThrowable()));
								AbstractStepResult.failed(((BeforeOrAfterStep) step).method(), exWrapper)
										.describeTo(state.storyRunner().storyReporter());
							} else if (step instanceof ScenarioStep) {
								exWrapper = new UUIDExceptionWrapper(step.stepAsString(), jUnitResult.getThrowable());
								AbstractStepResult.failed(step.stepAsString(), exWrapper).describeTo(
										state.storyRunner().storyReporter());
							} else {
								System.err.println("step of unexpected type: " + step.getClass().getName());
							}
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
				/*
				 * initialMethod.getDeclaringClass().getSimpleName() + "_" +
				 */initialMethod.getDeclaringClass().getSimpleName() + "_" + initialMethod.getName(),
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
				/*
				 * initialMethod.getDeclaringClass().getSimpleName() + "_" +
				 */initialMethod.getDeclaringClass().getSimpleName() + "_" + initialMethod.getName(),
						step.method().getParameterTypes());
				final FrameworkMethod method = new FrameworkMethod(stepMethodClone);
				ArquillianJbehaveRunner.this.withAfters(method, testClassInstance, new EmptyStatement()).evaluate();
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				remoteExecutedStep = step;
			}
		}
	}

	private static class EmptyStatement extends Statement {
		@Override
		public void evaluate() throws Throwable {
		}
	}

	private boolean shouldInvoke(Step step) {
		if (!isRunningRemote) {
			// always permit a step to run in local mode (they are ran in order
			// by jbehave
			return true;
		}
		// in container JBehave restarts the process every time so we have to
		// skip steps until we reach the correct one

		// prevent remote ArquillianJBehaveRunner from running more then one
		// pending step at a time
		if (remoteExecutedStep != null) {
			return false;
		}
		// if step is pending, return false but test to see if it could be
		// executed in case it wasn't pending
		// this is needed to keep the track of running progress (successfully
		// executing other scenarios after previous one got pending steps)
		if (step instanceof PendingStep && ((PendingStep) step).index(storyPaths).equals(stepToRun)) {
			// mark it as executed but return false to actually skip
			// execution - this prevents execution in remote of more then one step at a time
			remoteExecutedStep = step;
			return false;
		} else if (step instanceof ScenarioStep && ((ScenarioStep) step).index(storyPaths).equals(stepToRun)) {
			return true;
		} else if (step instanceof BeforeOrAfterStep) {
			Method m = step.method();
			assert m != null;
			Description testClassDescription = super.getDescription();
			for (Description d : testClassDescription.getChildren()) {
				if (d.getMethodName() != null
						&& d.getMethodName().equals(m.getDeclaringClass().getSimpleName() + "_" + m.getName())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * If the 2 states belong to different story runners then return a new state representing the old state but
	 * belonging to the new runner. Else the oldState is returned. This case appears when running in container, oldstate
	 * being the state from a previous ArquillianRunner invocation and newState from the current invocation. During an
	 * invocation, jbehave creates a new instance of StoryRunner
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
	
	private class StateResetStoryReporter extends NullStoryReporter {
		/**
		 * Reset the crrState after each scenario
		 */
		@Override
	    public void afterScenario() {
			// in container, reset the state of jbehave runner only if the last step
			// of the scenario was executed
			// locally, jbehave handles this
			if (remoteExecutedStep != null) {
				assert remoteExecutedStep instanceof ScenarioStep;
				if (((ScenarioStep)remoteExecutedStep).isLastInScenario()) {
					crrState = null;
				}
			}
	    }
	}
}
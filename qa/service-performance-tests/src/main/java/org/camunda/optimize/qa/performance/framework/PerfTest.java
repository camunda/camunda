package org.camunda.optimize.qa.performance.framework;

import org.camunda.optimize.qa.performance.steps.decorator.TimeMeasurementTestStepDecorator;

import java.util.LinkedList;
import java.util.List;

public class PerfTest {

  private List<PerfTestStep> testSteps;
  private PerfTestContext context;

  public PerfTest(PerfTestConfiguration configuration) {
    testSteps = new LinkedList<>();
    context = new PerfTestContext(configuration);
  }

  public void addTestStep(PerfTestStep testStep) {
    // decorate the test steps here to extend the behavior
    PerfTestStep timedTestStep = new TimeMeasurementTestStepDecorator(testStep);
    testSteps.add(timedTestStep);
  }

  public PerfTestResult run() {
    PerfTestResult testResult = new PerfTestResult();
    for (PerfTestStep testStep : testSteps) {
      PerfTestStepResult stepResult = testStep.execute(context);
      testResult.addStepResult(testStep.getTestStepClass(), stepResult);
    }
    return testResult;
  }

}

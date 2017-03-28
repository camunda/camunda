package org.camunda.optimize.qa.performance.steps.decorator;

import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStep;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeMeasurementTestStepDecorator extends PerfTestStep {

  private Logger logger;

  private PerfTestStep testStep;

  public TimeMeasurementTestStepDecorator(PerfTestStep testStep) {
    this.testStep = testStep;
    logger = LoggerFactory.getLogger(testStep.getTestStepClass());
  }

  @Override
  public Class getTestStepClass() {
    return testStep.getTestStepClass();
  }

  @Override
  public PerfTestStepResult execute(PerfTestContext context) {

    long stepStartTime, stepEndTime;
    logger.info("Starting  " + testStep.getClass().getSimpleName());
    stepStartTime = System.currentTimeMillis();

    PerfTestStepResult result = testStep.execute(context);

    stepEndTime = System.currentTimeMillis();
    long totalTime = stepEndTime - stepStartTime;
    result.setDurationInMs(totalTime);
    logger.info("Finished " + testStep.getClass().getSimpleName());
    logger.info("Step took " + totalTime + " ms to finish!\n");

    return result;
  }
}

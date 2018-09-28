package org.camunda.optimize.test.it.rule;

import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

/**
 * Starts optimize during the test execution in maven. Also
 * ensures a clean shutdown of optimize after all tests are done.
 */
public class StartOptimizeExecutionListener extends RunListener {

  private Logger logger = LoggerFactory.getLogger(StartOptimizeExecutionListener.class);

  @Override
  public void testRunStarted(Description description) throws Exception {
     // Called before any tests have been run.
    try {
      TestEmbeddedCamundaOptimize.getInstance().start();
    } catch (Exception e) {
      logger.error("Failed to start Optimize", e);
    }
    waitUntilOptimizeIsStarted();
  }

  private void waitUntilOptimizeIsStarted() throws InterruptedException {
    OffsetDateTime timeout = OffsetDateTime.now().plusMinutes(8L);
    while(!TestEmbeddedCamundaOptimize.getInstance().isStarted()) {
      Thread.sleep(100L);
      if (OffsetDateTime.now().isAfter(timeout)) {
        throw new OptimizeIntegrationTestException("Optimize startup shouldn't take longer than 8 minutes");
      }
    }
  }

  @Override
  public void testRunFinished(Result result) throws Exception {
     // Called when all tests have finished
    TestEmbeddedCamundaOptimize.getInstance().destroy();
  }
}
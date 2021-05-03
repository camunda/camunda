package io.zeebe;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZeebeTestListener extends RunListener {

  private static final Logger LOG = LoggerFactory.getLogger("io.zeebe.test");

  @Override
  public void testStarted(final Description description) throws Exception {
    LOG.info("Test started: {}", description.getDisplayName());
  }

  @Override
  public void testFinished(final Description description) throws Exception {
    LOG.info("Test finished: {}", description.getDisplayName());
  }
}

package org.camunda.operate.qa.migration.performance;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationPerformanceTest {
  private static final Logger logger = LoggerFactory.getLogger(MigrationPerformanceTest.class);

  @Test
  public void testPerformanceDuration(){
    int timeoutInMinutes = 60;
    try {
      timeoutInMinutes = Integer.parseInt(System.getProperty("MIGRATION_TIMEOUT"));
    } catch (NumberFormatException e) {
      logger.error("Couldn't parse integer value of environment variable MIGRATION_TIMEOUT use default {} minutes.", timeoutInMinutes);
    }
    long timeout = TimeUnit.MINUTES.toMillis(timeoutInMinutes);
    Instant start = Instant.now();
    Instant finish = Instant.now();
    long timeElapsed = Duration.between(start, finish).toMillis();
    assertThat(timeElapsed)
        .as("Needed "+TimeUnit.MILLISECONDS.toMinutes(timeElapsed)+" of maximal "+timeoutInMinutes+" minutes.")
        .isLessThan(timeout);
  }
}

package org.camunda.optimize.service.util;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public class BackoffCalculator {
  private static final long STARTING_BACKOFF = 0;
  private long backoffCounter = STARTING_BACKOFF;
  private OffsetDateTime nextRetryTime = OffsetDateTime.now().minusMinutes(1L);
  private long maximumBackoff;
  private long interval;

  public BackoffCalculator(long maximumBackoff, long interval) {
    this.maximumBackoff = maximumBackoff;
    this.interval = interval;
  }

  public boolean isMaximumBackoffReached() {
    return backoffCounter >= maximumBackoff;
  }

  public long calculateSleepTime() {
    backoffCounter = Math.min(backoffCounter + 1, maximumBackoff);
    long sleepTimeInMs = interval * backoffCounter;
    nextRetryTime = nextRetryTime.plus(sleepTimeInMs, ChronoUnit.MILLIS);
    return sleepTimeInMs;
  }

  public long timeUntilNextRetryTime() {
    long backoffTime = OffsetDateTime.now().until(nextRetryTime, ChronoUnit.MILLIS);
    backoffTime = Math.max(0, backoffTime);
    return backoffTime;
  }

  public boolean isReadyForNextRetry() {
    return nextRetryTime.isBefore(OffsetDateTime.now());
  }

  public void resetBackoff() {
    backoffCounter = STARTING_BACKOFF;
    nextRetryTime = OffsetDateTime.now().minusMinutes(1L);
  }
}

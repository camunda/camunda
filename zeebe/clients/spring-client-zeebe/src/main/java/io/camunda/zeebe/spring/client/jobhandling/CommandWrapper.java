package io.camunda.zeebe.spring.client.jobhandling;

import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.BackoffSupplier;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandWrapper {

  private FinalCommandStep<Void> command;

  private ActivatedJob job;
  private CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;

  private long currentRetryDelay = 50l;
  private int invocationCounter = 0;
  private int maxRetries = 20; // TODO: Make configurable

  public CommandWrapper(
      FinalCommandStep<Void> command,
      ActivatedJob job,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy) {
    this.command = command;
    this.job = job;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
  }

  public void executeAsync() {
    invocationCounter++;
    command
        .send()
        .exceptionally(
            t -> {
              commandExceptionHandlingStrategy.handleCommandError(this, t);
              return null;
            });
  }

  public void increaseBackoffUsing(BackoffSupplier backoffSupplier) {
    currentRetryDelay = backoffSupplier.supplyRetryDelay(currentRetryDelay);
  }

  public void scheduleExecutionUsing(ScheduledExecutorService scheduledExecutorService) {
    scheduledExecutorService.schedule(this::executeAsync, currentRetryDelay, TimeUnit.MILLISECONDS);
  }

  @Override
  public String toString() {
    return "{"
        + "command="
        + command.getClass()
        + ", job="
        + job
        + ", currentRetryDelay="
        + currentRetryDelay
        + '}';
  }

  public boolean hasMoreRetries() {
    if (jobDeadlineExceeded()) {
      // it does not make much sense to retry if the deadline is over, the job will be assigned to
      // an other worker anyway
      return false;
    }
    return (invocationCounter < maxRetries);
  }

  public boolean jobDeadlineExceeded() {
    return (Instant.now().getEpochSecond() > job.getDeadline());
  }
}

package org.camunda.optimize.qa.performance.framework;

public class PerfTestStepResult<T> {

  private long durationInMs;
  private T result;

  public void setResult(T result) {
    this.result = result;
  }

  public T getResult() {
    return this.result;
  }

  public long getDurationInMs() {
    return durationInMs;
  }

  public void setDurationInMs(long durationInMs) {
    this.durationInMs = durationInMs;
  }
}

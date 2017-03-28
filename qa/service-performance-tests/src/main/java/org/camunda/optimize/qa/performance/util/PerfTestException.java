package org.camunda.optimize.qa.performance.util;

public class PerfTestException extends RuntimeException {

  public PerfTestException() {
    super();
  }

  public PerfTestException(String message, Throwable cause) {
    super(message, cause);
  }

  public PerfTestException(String message) {
    super(message);
  }

  public PerfTestException(Throwable cause) {
    super(cause);
  }
}

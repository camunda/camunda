package org.camunda.optimize.exception;

public class OptimizeIntegrationTestException extends RuntimeException {

  public OptimizeIntegrationTestException() {
    super();
  }

  public OptimizeIntegrationTestException(Exception e) {
    super(e);
  }

  public OptimizeIntegrationTestException(String message) {
    super(message);
  }
}

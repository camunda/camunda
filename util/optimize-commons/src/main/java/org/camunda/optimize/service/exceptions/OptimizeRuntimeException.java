package org.camunda.optimize.service.exceptions;


public class OptimizeRuntimeException extends RuntimeException {
  public OptimizeRuntimeException () {
    super();
  }

  public OptimizeRuntimeException(String message) {
    super(message);
  }

  public OptimizeRuntimeException(String message, Exception e) {
    super(message, e);
  }
}

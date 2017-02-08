package org.camunda.optimize.service.exceptions;

/**
 * @author Askar Akhmerov
 */
public class OptimizeRuntimeException extends RuntimeException {
  public OptimizeRuntimeException () {
    super();
  }

  public OptimizeRuntimeException(String message) {
    super(message);
  }
}

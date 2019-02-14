package org.camunda.optimize.service.exceptions;


public class OptimizeProcessDefinitionFetchException extends Exception {
  public OptimizeProcessDefinitionFetchException() {
    super();
  }

  public OptimizeProcessDefinitionFetchException(String message) {
    super(message);
  }

  public OptimizeProcessDefinitionFetchException(String message, Exception e) {
    super(message, e);
  }
}

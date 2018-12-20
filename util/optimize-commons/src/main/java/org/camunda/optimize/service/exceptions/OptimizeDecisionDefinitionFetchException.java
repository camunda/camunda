package org.camunda.optimize.service.exceptions;


public class OptimizeDecisionDefinitionFetchException extends RuntimeException {
  public OptimizeDecisionDefinitionFetchException() {
    super();
  }

  public OptimizeDecisionDefinitionFetchException(String message) {
    super(message);
  }

  public OptimizeDecisionDefinitionFetchException(String message, Exception e) {
    super(message, e);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions;


public class OptimizeDecisionDefinitionFetchException extends Exception {
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

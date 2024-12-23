/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.exception;

import java.util.Map;

/**
 * Indicates an error in sense of BPMN occured, that should be handled by the BPMN process, see
 * https://docs.camunda.io/docs/reference/bpmn-processes/error-events/error-events/
 */
public class CamundaBpmnError extends RuntimeException {

  private final String errorCode;
  private final String errorMessage;
  private final Map<String, Object> variables;

  public CamundaBpmnError(
      final String errorCode, final String errorMessage, final Map<String, Object> variables) {
    super("[" + errorCode + "] " + errorMessage);
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.variables = variables;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IncidentErrorType {
  AD_HOC_SUB_PROCESS_NO_RETRIES("AD_HOC_SUB_PROCESS_NO_RETRIES"),
  CALLED_DECISION_ERROR("CALLED_DECISION_ERROR"),
  CALLED_ELEMENT_ERROR("CALLED_ELEMENT_ERROR"),
  CONDITION_ERROR("CONDITION_ERROR"),
  DECISION_EVALUATION_ERROR("DECISION_EVALUATION_ERROR"),
  EXECUTION_LISTENER_NO_RETRIES("EXECUTION_LISTENER_NO_RETRIES"),
  EXTRACT_VALUE_ERROR("EXTRACT_VALUE_ERROR"),
  FORM_NOT_FOUND("FORM_NOT_FOUND"),
  IO_MAPPING_ERROR("IO_MAPPING_ERROR"),
  JOB_NO_RETRIES("JOB_NO_RETRIES"),
  MESSAGE_SIZE_EXCEEDED("MESSAGE_SIZE_EXCEEDED"),
  RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND"),
  TASK_LISTENER_NO_RETRIES("TASK_LISTENER_NO_RETRIES"),
  UNHANDLED_ERROR_EVENT("UNHANDLED_ERROR_EVENT"),
  UNKNOWN("UNKNOWN"),
  UNSPECIFIED("UNSPECIFIED");

  private final String value;

  IncidentErrorType(final String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static IncidentErrorType fromValue(final String value) {
    for (final IncidentErrorType b : IncidentErrorType.values()) {
      if (b.value.equalsIgnoreCase(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ErrorType {
  UNSPECIFIED("Unspecified"),
  UNKNOWN("Unknown"),
  IO_MAPPING_ERROR("I/O mapping error"),
  JOB_NO_RETRIES("No more retries left", true),
  EXECUTION_LISTENER_NO_RETRIES("Execution listener no more retries left", true),
  CONDITION_ERROR("Condition error"),
  EXTRACT_VALUE_ERROR("Extract value error"),
  CALLED_ELEMENT_ERROR("Called element error"),
  UNHANDLED_ERROR_EVENT("Unhandled error event"),
  MESSAGE_SIZE_EXCEEDED("Message size exceeded"),
  CALLED_DECISION_ERROR("Called decision error"),
  DECISION_EVALUATION_ERROR("Decision evaluation error"),

  FORM_NOT_FOUND("Form not found"),
  RESOURCE_NOT_FOUND("Resource not found");

  private static final Logger LOGGER = LoggerFactory.getLogger(ErrorType.class);

  private final String title;
  private final boolean resolvedViaRetries;

  ErrorType(final String title) {
    this(title, false);
  }

  ErrorType(final String title, boolean resolvedViaRetries) {
    this.title = title;
    this.resolvedViaRetries = resolvedViaRetries;
  }

  public static ErrorType fromZeebeErrorType(String errorType) {
    if (errorType == null) {
      return UNSPECIFIED;
    }
    try {
      return ErrorType.valueOf(errorType);
    } catch (IllegalArgumentException ex) {
      LOGGER.error(
          "Error type not found for value [{}]. UNKNOWN type will be assigned.", errorType);
      return UNKNOWN;
    }
  }

  public String getTitle() {
    return title;
  }

  public boolean isResolvedViaRetries() {
    return resolvedViaRetries;
  }
}

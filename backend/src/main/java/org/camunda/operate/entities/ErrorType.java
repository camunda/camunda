/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

/**
 * 
 * @deprecated 
 * Use {@link io.zeebe.protocol.ErrorType} instead.
 */
@Deprecated
public enum ErrorType {

  UNKNOWN("Unknown"),

  IO_MAPPING_ERROR("I/O mapping error"),

  JOB_NO_RETRIES("No more retries left"),

  CONDITION_ERROR("Condition error"),

  EXTRACT_VALUE_ERROR("Extract value error");

  private String title;

  ErrorType(String title) {
    this.title = title;
  }

  public String getTitle() {
    return title;
  }

  public static ErrorType createFrom(String errorType) {
    final ErrorType errorTypeEnum = valueOf(errorType);
    if (errorTypeEnum != null) {
      return errorTypeEnum;
    } else {
      return UNKNOWN;
    }
  }
}

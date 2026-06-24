/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

public record ErrorResponse(ErrorCode code, String message) {

  public enum ErrorCode {
    INVALID_REQUEST,
    OPERATION_NOT_ALLOWED,
    CONCURRENT_MODIFICATION,
    INTERNAL_ERROR;
  }
}

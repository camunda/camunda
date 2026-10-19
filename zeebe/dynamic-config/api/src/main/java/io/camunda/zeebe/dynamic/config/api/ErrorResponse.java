/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

public record ErrorResponse(ErrorCode code, String message) {

  /**
   * Maps this error response to the corresponding typed {@link
   * ClusterConfigurationRequestFailedException} subtype so callers can distinguish, for example, a
   * {@link ClusterConfigurationRequestFailedException.NotFound} (missing or disabled tenant) from
   * an {@link ClusterConfigurationRequestFailedException.InternalError}.
   */
  public RuntimeException toException() {
    return switch (code) {
      case INVALID_REQUEST ->
          new ClusterConfigurationRequestFailedException.InvalidRequest(message);
      case OPERATION_NOT_ALLOWED ->
          new ClusterConfigurationRequestFailedException.OperationNotAllowed(message);
      case CONCURRENT_MODIFICATION ->
          new ClusterConfigurationRequestFailedException.ConcurrentModificationException(message);
      case INTERNAL_ERROR -> new ClusterConfigurationRequestFailedException.InternalError(message);
      case INVALID_STATE -> new ClusterConfigurationRequestFailedException.InvalidState(message);
      case NOT_FOUND -> new ClusterConfigurationRequestFailedException.NotFound(message);
    };
  }

  public enum ErrorCode {
    INVALID_REQUEST,
    OPERATION_NOT_ALLOWED,
    CONCURRENT_MODIFICATION,
    INTERNAL_ERROR,
    INVALID_STATE,
    NOT_FOUND;
  }
}

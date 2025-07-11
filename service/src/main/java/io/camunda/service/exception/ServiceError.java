/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import io.camunda.service.exception.ServiceException.Status;

public record ServiceError(String message, Status status) {
  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    String message;
    Status status;

    public ServiceError build() {
      return new ServiceError(message, status);
    }

    public Builder message(final String message) {
      this.message = message;
      return this;
    }

    public Builder status(final Status status) {
      this.status = status;
      return this;
    }

    public void mergeFrom(final ServiceError other) {
      message = other.message == null ? message : other.message;
      status = other.status == null ? status : other.status;
    }
  }
}

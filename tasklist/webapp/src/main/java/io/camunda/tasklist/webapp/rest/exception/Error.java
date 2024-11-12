/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.rest.exception;

import io.swagger.v3.oas.annotations.media.Schema;

public class Error {

  @Schema(
      description =
          "An integer that represents the HTTP status code of the error response. For example, 400 indicates a 'Bad Request' error, 404 indicates a 'Not Found' error, and so on.",
      format = "int32")
  private int status;

  @Schema(description = "A string that provides a brief description of the error that occurred.")
  private String message;

  @Schema(description = "Error instance UUID for lookup (e.g., in log messages).")
  private String instance;

  public int getStatus() {
    return status;
  }

  public Error setStatus(final int status) {
    this.status = status;
    return this;
  }

  public String getInstance() {
    return instance;
  }

  public Error setInstance(final String instance) {
    this.instance = instance;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public Error setMessage(final String message) {
    this.message = message;
    return this;
  }
}

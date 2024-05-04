/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class TasklistElasticsearchConnectionException extends TasklistRuntimeException {

  public TasklistElasticsearchConnectionException() {}

  public TasklistElasticsearchConnectionException(String message) {
    super(message);
  }

  public TasklistElasticsearchConnectionException(String message, Throwable cause) {
    super(message, cause);
  }

  public TasklistElasticsearchConnectionException(Throwable cause) {
    super(cause);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.management;

import io.camunda.tasklist.exceptions.TasklistElasticsearchConnectionException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.APIException;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class ManagementAPIErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManagementAPIErrorController.class);

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(TasklistRuntimeException.class)
  public ResponseEntity<Error> handleException(final TasklistRuntimeException exception) {
    LOGGER.error(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(APIException.class)
  public ResponseEntity<Error> handleInternalAPIException(final APIException exception) {
    LOGGER.error(
        String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error =
        new Error()
            .setStatus(HttpStatus.BAD_REQUEST.value())
            .setInstance(exception.getInstance())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(NotFoundApiException.class)
  public ResponseEntity<Error> handleNotFound(final NotFoundApiException exception) {
    LOGGER.error(
        String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error =
        new Error()
            .setStatus(HttpStatus.NOT_FOUND.value())
            .setInstance(exception.getInstance())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  @ExceptionHandler(TasklistElasticsearchConnectionException.class)
  public ResponseEntity<Error> handleNotFound(
      final TasklistElasticsearchConnectionException exception) {
    LOGGER.error(exception.getMessage(), exception);
    final Error error =
        new Error().setStatus(HttpStatus.BAD_GATEWAY.value()).setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }
}

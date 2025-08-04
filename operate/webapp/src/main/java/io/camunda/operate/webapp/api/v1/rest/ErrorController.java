/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class ErrorController {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Error> handleAccessDeniedException(final AccessDeniedException exception) {
    logger.error(getSummary(exception));
    logger.debug(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setType(exception.getClass().getSimpleName())
            .setInstance(UUID.randomUUID().toString())
            .setStatus(HttpStatus.FORBIDDEN.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Error> handleForbiddenException(final ForbiddenException exception) {
    logger.error(getSummary(exception));
    logger.debug(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setType(exception.getClass().getSimpleName())
            .setInstance(UUID.randomUUID().toString())
            .setStatus(HttpStatus.FORBIDDEN.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ClientException.class)
  public ResponseEntity<Error> handleInvalidRequest(final ClientException exception) {
    logger.error(getSummary(exception));
    logger.debug(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setType(ClientException.TYPE)
            .setInstance(exception.getInstance())
            .setStatus(HttpStatus.BAD_REQUEST.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> handleException(final Exception exception) {
    // Show client only detail message, log all messages
    return handleInvalidRequest(new ClientException(getOnlyDetailMessage(exception), exception));
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Error> handleInvalidRequest(final ValidationException exception) {
    logger.error(getSummary(exception));
    logger.debug(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setType(ValidationException.TYPE)
            .setInstance(exception.getInstance())
            .setStatus(HttpStatus.BAD_REQUEST.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<Error> handleNotFound(final ResourceNotFoundException exception) {
    logger.error(getSummary(exception));
    logger.debug(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setType(ResourceNotFoundException.TYPE)
            .setInstance(exception.getInstance())
            .setStatus(HttpStatus.NOT_FOUND.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(ServerException.class)
  public ResponseEntity<Error> handleServerException(final ServerException exception) {
    logger.error(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setType(ServerException.TYPE)
            .setInstance(exception.getInstance())
            .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  private String getOnlyDetailMessage(final Exception exception) {
    return StringUtils.substringBefore(exception.getMessage(), "; nested exception is");
  }

  private String getSummary(final Exception exception) {
    return String.format("%s: %s", exception.getClass().getSimpleName(), exception.getMessage());
  }
}

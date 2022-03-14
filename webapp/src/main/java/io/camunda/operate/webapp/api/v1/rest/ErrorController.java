/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.api.v1.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class ErrorController {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ClientException.class)
  public ResponseEntity<Error> handleInvalidRequest(ClientException exception) {
    logger.info(exception.getMessage(), exception);
    final Error error = new Error()
        .setType(ClientException.TYPE)
        .setInstance(exception.getInstance())
        .setStatus(HttpStatus.BAD_REQUEST.value())
        .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Error> handleInvalidRequest(ValidationException exception) {
    logger.info(exception.getMessage(), exception);
    final Error error = new Error()
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
  public ResponseEntity<Error> handleNotFound(ResourceNotFoundException exception) {
    logger.info(exception.getMessage(), exception);
    final Error error = new Error()
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
  public ResponseEntity<Error> handleServerException(ServerException exception) {
    logger.error(exception.getMessage(), exception);
    final Error error = new Error()
        .setType(ServerException.TYPE)
        .setInstance(exception.getInstance())
        .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }
}

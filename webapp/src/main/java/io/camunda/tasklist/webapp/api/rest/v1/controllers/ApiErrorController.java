/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.APIException;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.rest.exception.UnauthenticatedUserException;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class ApiErrorController {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  @Hidden
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(TasklistRuntimeException.class)
  public ResponseEntity<Error> handleException(TasklistRuntimeException exception) {
    logger.warn(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @Hidden
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> handleException(Exception exception) {
    logger.error(
        String.format("Unexpected exception happened: %s", exception.getMessage()), exception);
    final Error error =
        new Error()
            .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .setMessage("Unexpected server error occurred.");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @Hidden
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(APIException.class)
  public ResponseEntity<Error> handleAPIException(APIException exception) {
    logAPIException(exception);
    final Error error =
        new Error()
            .setStatus(HttpStatus.BAD_REQUEST.value())
            .setInstance(exception.getInstance())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @Hidden
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Error> handleMissingRequestParameterException(
      MissingServletRequestParameterException exception) {

    logger.warn(exception.getMessage(), exception);
    return handleAPIException(new InvalidRequestException(exception.getMessage()));
  }

  private void logAPIException(APIException exception) {
    logger.warn(
        String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()),
        exception);
  }

  @Hidden
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ExceptionHandler(UnauthenticatedUserException.class)
  public ResponseEntity<Error> handleUnauthenticatedUserException(
      UnauthenticatedUserException exception) {
    logger.warn(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setInstance(UUID.randomUUID().toString())
            .setStatus(HttpStatus.UNAUTHORIZED.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @Hidden
  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(ForbiddenActionException.class)
  public ResponseEntity<Error> handleForbiddenActionException(ForbiddenActionException exception) {
    logAPIException(exception);
    final Error error =
        new Error()
            .setInstance(exception.getInstance())
            .setStatus(HttpStatus.FORBIDDEN.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @Hidden
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Error> handleNotFound(NotFoundException exception) {
    logAPIException(exception);
    final Error error =
        new Error()
            .setInstance(exception.getInstance())
            .setStatus(HttpStatus.NOT_FOUND.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.rest;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.APIException;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

public abstract class InternalAPIErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(InternalAPIErrorController.class);

  @Autowired private TasklistProfileService tasklistProfileService;

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(TasklistRuntimeException.class)
  public ResponseEntity<Error> handleException(TasklistRuntimeException exception) {
    LOGGER.warn(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .setMessage(tasklistProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(APIException.class)
  public ResponseEntity<Error> handleInternalAPIException(APIException exception) {
    LOGGER.warn(String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error =
        new Error()
            .setStatus(HttpStatus.BAD_REQUEST.value())
            .setInstance(exception.getInstance())
            .setMessage(tasklistProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(ForbiddenActionException.class)
  public ResponseEntity<Error> handleAccessDeniedException(ForbiddenActionException exception) {
    LOGGER.warn(exception.getMessage(), exception);
    final Error error =
        new Error()
            .setInstance(UUID.randomUUID().toString())
            .setStatus(HttpStatus.FORBIDDEN.value())
            .setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Error> handleNotFound(NotFoundException exception) {
    LOGGER.warn(String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error =
        new Error()
            .setStatus(HttpStatus.NOT_FOUND.value())
            .setInstance(exception.getInstance())
            .setMessage(tasklistProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }
}

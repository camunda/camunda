/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.rest.exception.Error;
import io.camunda.operate.webapp.rest.exception.InternalAPIException;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.OperateProfileService;
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

  @Autowired
  private OperateProfileService operateProfileService;

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(OperateRuntimeException.class)
  public ResponseEntity<Error> handleInternalAPIException(OperateRuntimeException exception) {
    LOGGER.warn(exception.getMessage(), exception);
    final Error error = new Error()
        .setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
        .setMessage(operateProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(InternalAPIException.class)
  public ResponseEntity<Error> handleInternalAPIException(InternalAPIException exception) {
    LOGGER.warn(String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error = new Error()
        .setStatus(HttpStatus.BAD_REQUEST.value())
        .setInstance(exception.getInstance())
        .setMessage(operateProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Error> handleNotFound(NotFoundException exception) {
    LOGGER.warn(String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error = new Error()
        .setStatus(HttpStatus.NOT_FOUND.value())
        .setInstance(exception.getInstance())
        .setMessage(operateProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ExceptionHandler(NotAuthorizedException.class)
  public ResponseEntity<Error> handleNotAuthorized(NotAuthorizedException exception) {
    LOGGER.warn(String.format("Instance: %s; %s", exception.getInstance(), exception.getMessage()));
    final Error error = new Error()
        .setStatus(HttpStatus.FORBIDDEN.value())
        .setInstance(exception.getInstance())
        .setMessage(operateProfileService.getMessageByProfileFor(exception));
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }
}

/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.APIException;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.rest.exception.UnauthenticatedUserException;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Error> handleException(HttpMessageNotReadableException exception) {
    logger.warn(exception.getMessage(), exception);
    final Error error =
        new Error().setStatus(HttpStatus.BAD_REQUEST.value()).setMessage(exception.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(error);
  }

  @Hidden
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Error> handleNotFound(NotFoundException exception) {
    return handleNotFound(new NotFoundApiException(exception.getMessage(), exception));
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
  @ExceptionHandler(NotFoundApiException.class)
  public ResponseEntity<Error> handleNotFound(NotFoundApiException exception) {
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

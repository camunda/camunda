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
package io.camunda.operate.webapp.api.v1.rest;

import io.camunda.operate.webapp.api.v1.entities.Error;
import io.camunda.operate.webapp.api.v1.exceptions.ClientException;
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
  public ResponseEntity<Error> handleAccessDeniedException(AccessDeniedException exception) {
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
  public ResponseEntity<Error> handleInvalidRequest(ClientException exception) {
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
  public ResponseEntity<Error> handleException(Exception exception) {
    // Show client only detail message, log all messages
    return handleInvalidRequest(new ClientException(getOnlyDetailMessage(exception), exception));
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Error> handleInvalidRequest(ValidationException exception) {
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
  public ResponseEntity<Error> handleNotFound(ResourceNotFoundException exception) {
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
  public ResponseEntity<Error> handleServerException(ServerException exception) {
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

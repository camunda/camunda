/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

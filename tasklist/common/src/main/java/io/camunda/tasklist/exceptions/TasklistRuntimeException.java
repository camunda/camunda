/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.exceptions;

public class TasklistRuntimeException extends RuntimeException {

  public TasklistRuntimeException() {}

  public TasklistRuntimeException(String message) {
    super(message);
  }

  public TasklistRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public TasklistRuntimeException(Throwable cause) {
    super(cause);
  }
}

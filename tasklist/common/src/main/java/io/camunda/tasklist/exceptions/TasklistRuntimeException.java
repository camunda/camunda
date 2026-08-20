/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

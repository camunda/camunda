/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.rest.exception;

public class UserNotFoundApiException extends NotFoundApiException {

  private static final long serialVersionUID = 1L;

  public UserNotFoundApiException(String message) {
    super(message);
  }

  public UserNotFoundApiException(String message, Throwable cause) {
    super(message, cause);
  }
}

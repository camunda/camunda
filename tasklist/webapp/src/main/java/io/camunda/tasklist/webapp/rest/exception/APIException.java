/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.rest.exception;

import java.util.UUID;

public abstract class APIException extends RuntimeException {

  private final String instance = UUID.randomUUID().toString();

  protected APIException(final String message) {
    super(message);
  }

  protected APIException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public String getInstance() {
    return instance;
  }
}

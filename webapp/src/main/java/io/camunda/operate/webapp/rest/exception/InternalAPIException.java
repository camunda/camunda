/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.exception;

import java.util.UUID;

public abstract class InternalAPIException extends RuntimeException {

  private String instance = UUID.randomUUID().toString();

  protected InternalAPIException(final String message) {
    super(message);
  }

  protected InternalAPIException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public String getInstance() {
    return instance;
  }

  public InternalAPIException setInstance(String instance) {
    this.instance = instance;
    return this;
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities;

public class Error {
  private int status;
  private String message;
  private String instance;
  private String type;

  public String getType() {
    return type;
  }

  public Error setType(final String type) {
    this.type = type;
    return this;
  }

  public int getStatus() {
    return status;
  }

  public Error setStatus(final int status) {
    this.status = status;
    return this;
  }

  public String getInstance() {
    return instance;
  }

  public Error setInstance(final String instance) {
    this.instance = instance;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public Error setMessage(final String message) {
    this.message = message;
    return this;
  }
}

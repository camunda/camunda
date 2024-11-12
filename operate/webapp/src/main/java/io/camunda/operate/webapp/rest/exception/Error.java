/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.exception;

public class Error {
  private int status;
  private String message;
  private String instance;

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

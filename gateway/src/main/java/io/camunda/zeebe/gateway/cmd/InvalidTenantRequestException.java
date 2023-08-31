/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.cmd;

public class InvalidTenantRequestException extends ClientException {

  private static final String MESSAGE_FORMAT = "Invalid tenant-aware %s request: %s";

  private final String requestName;
  private final String reason;

  public InvalidTenantRequestException(final String requestName, final String reason) {
    super(String.format(MESSAGE_FORMAT, requestName, reason));

    this.requestName = requestName;
    this.reason = reason;
  }

  public String getRequestName() {
    return requestName;
  }

  public String getReason() {
    return reason;
  }
}

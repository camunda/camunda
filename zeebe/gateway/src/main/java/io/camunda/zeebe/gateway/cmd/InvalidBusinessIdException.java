/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.cmd;

public class InvalidBusinessIdException extends ClientException {

  private static final String MESSAGE_FORMAT =
      "Expected to handle request with business id '%s', but %s";

  private final String businessId;
  private final String reason;

  public InvalidBusinessIdException(final String businessId, final String reason) {
    super(String.format(MESSAGE_FORMAT, businessId, reason));

    this.businessId = businessId;
    this.reason = reason;
  }

  public String getBusinessId() {
    return businessId;
  }

  public String getReason() {
    return reason;
  }
}

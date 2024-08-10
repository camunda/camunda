/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;

/** A client command was rejected by the broker. */
public class BrokerRejectionException extends BrokerClientException {
  private static final String ERROR_MESSAGE_FORMAT = "Command (%s) rejected (%s): %s";

  private final BrokerRejection rejection;

  public BrokerRejectionException(final BrokerRejection rejection) {
    this(rejection, null);
  }

  public BrokerRejectionException(final BrokerRejection rejection, final Throwable cause) {
    super(
        String.format(
            ERROR_MESSAGE_FORMAT,
            rejection.intent().name(),
            rejection.type().name(),
            rejection.reason()),
        cause);
    this.rejection = rejection;
  }

  public BrokerRejection getRejection() {
    return rejection;
  }
}

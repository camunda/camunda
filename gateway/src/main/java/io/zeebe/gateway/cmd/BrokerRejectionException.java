/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

import io.zeebe.gateway.impl.broker.response.BrokerRejection;

/** A client command was rejected by the broker. */
public class BrokerRejectionException extends BrokerException {
  private static final long serialVersionUID = -4363984283411850284L;
  private static final String ERROR_MESSAGE_FORMAT = "Command (%s) rejected (%s): %s";

  private final BrokerRejection rejection;

  public BrokerRejectionException(final BrokerRejection rejection) {
    this(rejection, null);
  }

  public BrokerRejectionException(final BrokerRejection rejection, final Throwable cause) {
    super(
        String.format(
            ERROR_MESSAGE_FORMAT,
            rejection.getIntent().name(),
            rejection.getType().name(),
            rejection.getReason()),
        cause);
    this.rejection = rejection;
  }

  public BrokerRejection getRejection() {
    return rejection;
  }
}

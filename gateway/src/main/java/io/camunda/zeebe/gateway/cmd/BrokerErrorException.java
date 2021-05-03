/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

import io.zeebe.gateway.impl.broker.response.BrokerError;

public class BrokerErrorException extends BrokerException {
  private static final long serialVersionUID = 1L;
  private static final String ERROR_MESSAGE_FORMAT = "Received error from broker (%s): %s";

  protected final BrokerError error;

  public BrokerErrorException(final BrokerError brokerError) {
    this(brokerError, null);
  }

  public BrokerErrorException(final BrokerError error, final Throwable cause) {
    super(String.format(ERROR_MESSAGE_FORMAT, error.getCode(), error.getMessage()), cause);
    this.error = error;
  }

  public BrokerError getError() {
    return error;
  }
}

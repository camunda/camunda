/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

public final class IllegalBrokerResponseException extends BrokerResponseException {

  private static final long serialVersionUID = -5363931482936307555L;

  public IllegalBrokerResponseException(final String message) {
    super(message);
  }

  public IllegalBrokerResponseException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public IllegalBrokerResponseException(final Throwable cause) {
    super(cause);
  }
}

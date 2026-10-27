/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

public final class NoTopologyAvailableException extends BrokerClientException {
  private static final String DEFAULT_MESSAGE =
      "Expected to send the request to a partition in the topology, but gateway does not know broker topology."
          + " Please try again later. If the error persists contact your zeebe operator.";

  public NoTopologyAvailableException() {
    this(DEFAULT_MESSAGE);
  }

  public NoTopologyAvailableException(final String message) {
    super(message);
  }

  public NoTopologyAvailableException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

public final class NoTopologyAvailableException extends ClientException {
  private static final String DEFAULT_MESSAGE =
      "Expected to send the request to a partition in the topology, but gateway does not know broker topology."
          + " Please try again later. If the error persists contact your zeebe operator.";
  private static final long serialVersionUID = 7035483927294101779L;

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

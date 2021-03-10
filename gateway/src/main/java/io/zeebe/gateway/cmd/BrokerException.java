/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.cmd;

/**
 * Represents exceptional errors that occur in the gateway-broker client on the broker side, e.g.
 * error responses, command rejections, etc.
 *
 * <p>Primary usage is wrapping around error responses so that these can be consumed by throwable
 * handlers.
 */
public class BrokerException extends RuntimeException {

  private static final long serialVersionUID = -2808029505078161668L;

  public BrokerException(final String message) {
    super(message);
  }

  public BrokerException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public BrokerException(final Throwable cause) {
    super(cause);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api;

/**
 * Represents exceptional errors that occur in the gateway-broker client on the broker side, e.g.
 * error responses, command rejections, etc.
 *
 * <p>Primary usage is wrapping around error responses so that these can be consumed by throwable
 * handlers.
 */
public class BrokerClientException extends RuntimeException {

  public BrokerClientException(final String message) {
    super(message);
  }

  public BrokerClientException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public BrokerClientException(final Throwable cause) {
    super(cause);
  }
}

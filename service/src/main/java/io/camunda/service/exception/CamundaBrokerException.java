/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import java.util.Optional;

public class CamundaBrokerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final BrokerRejection brokerRejection;
  private final BrokerError brokerError;

  public CamundaBrokerException(final String message) {
    super(message);
    brokerError = null;
    brokerRejection = null;
  }

  public CamundaBrokerException(final String message, final Throwable cause) {
    super(message, cause);
    brokerRejection = null;
    brokerError = null;
  }

  public CamundaBrokerException(final Throwable cause) {
    super(cause);
    brokerRejection = null;
    brokerError = null;
  }

  public CamundaBrokerException(final BrokerError brokerError) {
    this.brokerError = brokerError;
    brokerRejection = null;
  }

  public CamundaBrokerException(final BrokerRejection brokerRejection) {
    this.brokerRejection = brokerRejection;
    brokerError = null;
  }

  public Optional<BrokerError> getBrokerError() {
    return Optional.ofNullable(brokerError);
  }

  public Optional<BrokerRejection> getBrokerRejection() {
    return Optional.ofNullable(brokerRejection);
  }
}

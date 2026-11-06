/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.cmd;

/**
 * Thrown on the gateway side when a request is invalid and must be answered with an {@code
 * INVALID_ARGUMENT} error, as opposed to an internal error.
 */
public class InvalidRequestException extends ClientException {

  public InvalidRequestException(final String message) {
    super(message);
  }

  public InvalidRequestException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

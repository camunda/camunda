/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

/**
 * Thrown when a connection probe detects that the client has disconnected during a pending
 * long-polling job activation request. This is an expected condition (e.g. client shutdown) and
 * triggers job reactivation via the existing {@code reactivateJobs()} path.
 */
public class ClientDisconnectedException extends RuntimeException {

  public ClientDisconnectedException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

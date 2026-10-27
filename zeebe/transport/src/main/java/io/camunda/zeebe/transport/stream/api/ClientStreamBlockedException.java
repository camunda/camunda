/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

/**
 * Exception thrown when a client is blocked, i.e. it cannot receive any pushed payloads until
 * further notice.
 */
public final class ClientStreamBlockedException extends Exception {

  public ClientStreamBlockedException(final String message) {
    super(message);
  }
}

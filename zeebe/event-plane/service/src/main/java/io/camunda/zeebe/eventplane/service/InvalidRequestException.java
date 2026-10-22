/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

/** A request that the service can't answer because of what it says. It becomes a 400. */
final class InvalidRequestException extends RuntimeException {

  private final String expected;
  private final String actual;

  InvalidRequestException(final String expected, final String actual) {
    super("Expected %s, but got %s".formatted(expected, actual));
    this.expected = expected;
    this.actual = actual;
  }

  /** What the request should have held. */
  String expected() {
    return expected;
  }

  /** What it held. */
  String actual() {
    return actual;
  }
}

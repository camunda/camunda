/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

/** The content of a consumer group's worker or cursor object isn't what the contract says. */
final class InvalidGroupObjectException extends RuntimeException {

  private final String key;

  InvalidGroupObjectException(final String key, final String expected, final String got) {
    super("Expected the object %s to hold %s, but got %s".formatted(key, expected, got));
    this.key = key;
  }

  /** The key of the object in the store. */
  String key() {
    return key;
  }
}

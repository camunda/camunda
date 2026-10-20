/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

/** A key, a prefix or a name that a store or {@link ObjectNames} can't use as given. */
public final class InvalidObjectNameException extends IllegalArgumentException {

  private final String name;

  public InvalidObjectNameException(final String expected, final String name) {
    super("Expected %s, but got '%s'".formatted(expected, name));
    this.name = name;
  }

  /** The key, prefix or name that was rejected. */
  public String name() {
    return name;
  }
}

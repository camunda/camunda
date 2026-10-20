/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

/** A message on the floor's subject that doesn't have a floor's length. */
public final class MalformedFloorMessageException extends IllegalArgumentException {

  private final int expectedLength;
  private final int length;

  public MalformedFloorMessageException(final int expectedLength, final int length) {
    super(
        "Expected an upload floor message of %d bytes, but got %d bytes"
            .formatted(expectedLength, length));
    this.expectedLength = expectedLength;
    this.length = length;
  }

  public int expectedLength() {
    return expectedLength;
  }

  public int length() {
    return length;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

/** An upload floor whose position is below the one the state holds: the floor never goes back. */
public final class FloorMovedBackException extends IllegalArgumentException {

  private final long currentPosition;
  private final long position;

  public FloorMovedBackException(final long currentPosition, final long position) {
    super(
        "Expected the upload floor to move forward from position %d, but got %d"
            .formatted(currentPosition, position));
    this.currentPosition = currentPosition;
    this.position = position;
  }

  public long currentPosition() {
    return currentPosition;
  }

  public long position() {
    return position;
  }
}

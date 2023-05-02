/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

/**
 * Contains positions which are related to the last processing, and are used to restore the
 * processing state machine.
 */
public class LastProcessingPositions {

  /** The last command position, which the processing state machine has processed successfully. */
  private final long lastProcessedPosition;

  /** The last written record position on the log. */
  private final long lastWrittenPosition;

  public LastProcessingPositions(final long lastProcessedPosition, final long lastWrittenPosition) {
    this.lastProcessedPosition = lastProcessedPosition;
    this.lastWrittenPosition = lastWrittenPosition;
  }

  public long getLastProcessedPosition() {
    return lastProcessedPosition;
  }

  public long getLastWrittenPosition() {
    return lastWrittenPosition;
  }

  @Override
  public String toString() {
    return "["
        + "lastProcessedPosition: "
        + lastProcessedPosition
        + ", lastWrittenPosition: "
        + lastWrittenPosition
        + ']';
  }
}

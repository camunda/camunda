/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import java.util.UUID;

/**
 * How far a reader got in a log: the last entry it delivered fully, and the last record it
 * delivered, which can lie in the entry after it if the reader stopped inside that entry.
 *
 * @param history the history whose path the indexes and positions are on
 * @param index the Raft index of the last entry delivered fully, 0 if none
 * @param position the position of the last record delivered, -1 if none
 */
public record Cursor(UUID history, long index, long position) implements StartFrom {

  public Cursor {
    if (index < 0 || position < -1) {
      throw new IllegalArgumentException(
          "Expected a cursor's index of 0 or more and position of -1 or more, but got %d and %d"
              .formatted(index, position));
    }
  }
}

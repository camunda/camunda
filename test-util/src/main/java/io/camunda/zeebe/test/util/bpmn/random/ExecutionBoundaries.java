/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random;

import java.util.Random;
import org.agrona.collections.IntArrayList;

/**
 * An execution boundary is the point where automatic and non-automatic {@link
 * ScheduledExecutionStep}'s meet each other. This class contains information about the existing
 * execution boundaries in a {@link ExecutionPath}.
 */
final class ExecutionBoundaries {

  private final IntArrayList boundaries = new IntArrayList();

  void addBoundary(final int index) {
    boundaries.add(index);
  }

  /**
   * The boundaries of automatic and non-automatic are evenly, this means we have either:
   *
   * <ul>
   *   <li>no non-automatic execution steps
   *   <li>only some at the end of the execution path
   *   <li>only some at the beginning
   * </ul>
   *
   * @return true if boundaries are equal
   */
  public boolean boundariesAreEvenly() {
    return boundaries.isEmpty() || boundaries.size() == 1;
  }

  public int getFirstExecutionBoundary() {
    return boundaries.isEmpty() ? 0 : boundaries.get(0);
  }

  /**
   * Returns a pseudo-randomly chosen execution boundary.
   *
   * @return the execution boundary
   */
  public int chooseRandomBoundary(final Random random) {
    return boundaries.isEmpty() ? 0 : boundaries.get(random.nextInt(boundaries.size()));
  }

  public int getLastExecutionBoundary() {
    return boundaries.isEmpty() ? 0 : boundaries.get(boundaries.size() - 1);
  }
}

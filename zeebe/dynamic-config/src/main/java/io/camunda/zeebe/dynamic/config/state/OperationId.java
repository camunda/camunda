/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import org.jspecify.annotations.NullMarked;

/**
 * Identifies one operation within a {@link DependencyChangePlan}.
 *
 * <p>Assigned once, by the coordinator, when the plan is built, and never reused within a plan. It
 * is the key both for declaring dependencies and for recording completion, so it has to be stable
 * across gossip and comparable for deterministic ordering — two brokers rendering the same plan
 * must produce the same list.
 */
@NullMarked
public record OperationId(int value) implements Comparable<OperationId> {

  public OperationId {
    if (value < 0) {
      throw new IllegalArgumentException("Operation id must be non-negative, but was " + value);
    }
  }

  public static OperationId of(final int value) {
    return new OperationId(value);
  }

  @Override
  public int compareTo(final OperationId other) {
    return Integer.compare(value, other.value);
  }

  @Override
  public String toString() {
    return "#" + value;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster;

import java.util.Comparator;
import org.jspecify.annotations.NonNull;

/** Identifies a partition within a partition group. */
public record PartitionId(String group, int number) implements Comparable<PartitionId> {

  private static final Comparator<PartitionId> COMPARATOR =
      Comparator.comparing(PartitionId::group).thenComparingInt(PartitionId::number);

  public PartitionId {
    if (group == null) {
      throw new IllegalArgumentException("group cannot be null");
    }
    if (number < 0) {
      throw new IllegalArgumentException("partition number must be non-negative");
    }
  }

  @Override
  public int compareTo(@NonNull final PartitionId o) {
    return COMPARATOR.compare(this, o);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import java.util.Map;

/**
 * A dataset admitted to the {@link DatasetRegistry}: its {@code cubeId}, {@link
 * DatasetDeclaration}, {@code schemaVersion} (bumped on a forward-only grain change), and — the
 * crux — an <b>immutable per-source-partition activation position vector</b> fixed at admission.
 * Stage 1 folds a fact into this cube only when the fact's position is at or after the cube's
 * activation for that partition, so the cube is forward-only and, because the vector never changes,
 * replay reproduces the exact same membership. A partition absent from the vector (e.g. added after
 * admission) activates from its start.
 */
public record RegisteredDataset(
    long cubeId, DatasetDeclaration declaration, Map<Integer, Long> activation, int schemaVersion) {

  public RegisteredDataset {
    activation = Map.copyOf(activation);
  }

  /** Whether a fact at {@code (partition, position)} belongs to this cube (forward-only). */
  public boolean admits(final int partition, final long position) {
    return position >= activation.getOrDefault(partition, 0L);
  }
}

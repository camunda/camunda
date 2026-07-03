/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

/**
 * The by-name field-access seam a derived fact exposes so dimensions and meters can be read
 * generically — the grouping key selector reads dimension fields through it, and (from step 1.3)
 * meters read their measure field through it. Deterministic: the same fact always returns the same
 * value for a field.
 *
 * <p>A missing field returns {@code null} (the "unknown" bucket for a dimension); a present value
 * is a {@code String}, {@code Long}, {@code Integer}, or {@code Boolean}, matching {@link
 * DimensionType}. This keeps the generic core independent of any concrete fact class: existing
 * facts expose a thin adapter, and the generic fact (a later phase) implements this natively.
 */
@FunctionalInterface
public interface FactRow {

  /** The value of {@code field}, or {@code null} if this fact has no such field. */
  Object get(String field);
}

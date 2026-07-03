/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

import io.camunda.analytics.dimension.DimensionType;
import java.util.Objects;

/**
 * One declared grouping dimension of a {@link DatasetDeclaration}: the fact {@code field} to group
 * by, its {@code type}, and — for a variable dimension ({@code var.*}) — the {@link
 * EnrichmentTiming enrichment} that decides which snapshot of the variable is stamped. Structural
 * dimensions (e.g. {@code bpmnProcessId}) ignore the timing. The compiler turns these, in order,
 * into the cube's {@code DimensionSchema}.
 */
public record DimensionSpec(String name, DimensionType type, EnrichmentTiming enrichment) {

  /**
   * The namespace prefix for a variable dimension: a dimension named {@code var.<name>} groups by
   * the process variable {@code <name>}. The projector stamps facts with this prefix and the
   * compiler detects variable dimensions by it — a model-level naming convention.
   */
  public static final String VARIABLE_PREFIX = "var.";

  public DimensionSpec {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(enrichment, "enrichment");
    if (name.isBlank()) {
      throw new IllegalArgumentException("dimension name must not be blank");
    }
  }

  /** A dimension with the default {@link EnrichmentTiming#EVENT_TIME} snapshot timing. */
  public static DimensionSpec of(final String name, final DimensionType type) {
    return new DimensionSpec(name, type, EnrichmentTiming.EVENT_TIME);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.catalog;

import java.util.List;
import java.util.Optional;

/**
 * One entity discovered from its {@code <entity>_metrics}/{@code <entity>_hist} partials views (see
 * {@link MetricRegistry}). {@code hasCnt} is whether the bare, unprefixed {@code cnt} column (a
 * plain {@code count()} declaration) is present; {@code counters} are any other bare count-only
 * column groups (a {@code _cnt} suffix with no sibling {@code _sum}) found alongside it, by their
 * unsuffixed name.
 */
public record EntityCatalog(
    String name,
    List<DimCatalog> dims,
    boolean hasCnt,
    List<String> counters,
    List<MeasureCatalog> measures,
    boolean hasHistTable) {

  /** Just the dim names, in declared order. */
  public List<String> dimNames() {
    return dims.stream().map(DimCatalog::name).toList();
  }

  /** The named measure's catalog entry, or empty if this entity has no such measure. */
  public Optional<MeasureCatalog> measure(final String measureName) {
    return measures.stream().filter(m -> m.name().equals(measureName)).findFirst();
  }

  public String metricsView() {
    return name + "_metrics";
  }

  public String histView() {
    return name + "_hist";
  }
}

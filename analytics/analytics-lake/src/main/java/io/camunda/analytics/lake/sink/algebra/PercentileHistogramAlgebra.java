/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.algebra;

import java.util.List;

/**
 * The shared shape of every {@link Algebra.PartialsShape#TALL} histogram algebra's own,
 * necessarily-standalone finalize SQL (a cumulative-count percentile walk — see {@link
 * Algebra#finalizeProjection}'s javadoc for why this cannot be a flat per-row projection like a
 * {@link Algebra.PartialsShape#WIDE} algebra's own). Implemented by {@link ExpHistogramAlgebra}
 * ({@code LONG} bins) and {@link SignedDoubleExpHistogramAlgebra} ({@code DOUBLE} bins) so {@code
 * io.camunda.analytics.lake.metrics.CompiledEntityMetrics#finalizeHistSql}/{@code
 * #finalizeHistMacroSql} can call whichever histogram-shaped algebra an entity actually declared
 * without needing to know which concrete class it is.
 */
public interface PercentileHistogramAlgebra {

  /**
   * Full, standalone SQL text estimating a percentile (the query's one positional {@code ?}
   * parameter, a value in {@code [0, 1]}) per {@code groupByColumns} group. See {@link
   * ExpHistogramAlgebra#finalizeQuery}/{@link SignedDoubleExpHistogramAlgebra#finalizeQuery} for
   * each implementation's own algorithm (textually identical between the two).
   */
  String finalizeQuery(String sourceTable, List<String> groupByColumns);

  /** Same idea as {@link #finalizeQuery}, packaged as a reusable DuckDB table macro. */
  String finalizeMacroSql(String macroName, String sourceTable, List<String> groupByColumns);
}

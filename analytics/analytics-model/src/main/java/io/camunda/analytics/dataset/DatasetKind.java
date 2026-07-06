/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset;

/**
 * What a {@link DatasetDeclaration} produces.
 *
 * <ul>
 *   <li>{@link #AGGREGATED} — a cube: windowed, grouped, mergeable meters shuffled and reduced into
 *       one cell per grain value per window (the default).
 *   <li>{@link #TABLE} — a raw dataset: a flat list of individual facts (e.g. process instances
 *       enriched with variables) over a time period, each written as one row keyed by a primary-key
 *       field. No windowing or aggregation — the row upsert is idempotent under replay, so it is
 *       written straight from Stage 1 without a shuffle.
 * </ul>
 */
public enum DatasetKind {
  AGGREGATED,
  TABLE
}

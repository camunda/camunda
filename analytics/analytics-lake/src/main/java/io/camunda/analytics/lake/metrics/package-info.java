/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Entity metrics declarations: a small builder ({@link
 * io.camunda.analytics.lake.metrics.EntityMetrics}) that compiles a dims/window/measures
 * declaration, validated against a raw {@link io.camunda.analytics.lake.sink.TableSchema}, into a
 * {@link io.camunda.analytics.lake.metrics.CompiledEntityMetrics}: a precomputed folding plan, the
 * two generated partials-table schemas, a stable fingerprint, and the generated merge/finalize SQL
 * assembled from {@link io.camunda.analytics.lake.sink.algebra}.
 *
 * <p>Like {@code io.camunda.analytics.lake.sink.algebra}, this package is pure library code: it has
 * no dependency on, and is not wired into, any pipeline in this codebase. A later milestone is
 * responsible for actually folding raw rows through a {@link
 * io.camunda.analytics.lake.metrics.RiderPlan} on a flush thread and running the generated SQL
 * against a real partials store.
 */
package io.camunda.analytics.lake.metrics;

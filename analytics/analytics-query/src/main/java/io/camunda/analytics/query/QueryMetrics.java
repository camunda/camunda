/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.query;

/**
 * The "is serving healthy" read-path signal: how long {@link DatasetQueryExecutor#execute} takes,
 * per dataset. A plain facade — this module is deliberately backend- and metrics-library-neutral
 * (no processing runtime, no instrumentation dependency); a wiring layer that already depends on a
 * concrete metrics library (e.g. the webapp, on Micrometer) supplies the implementation.
 */
public interface QueryMetrics {

  /** The no-op used when the caller wires no instrumentation. */
  QueryMetrics NOOP = (datasetName, durationNanos) -> {};

  /** One {@link DatasetQueryExecutor#execute} call against {@code datasetName} took this long. */
  void recordQueryDuration(String datasetName, long durationNanos);
}

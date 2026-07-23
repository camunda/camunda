/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.util.List;

/**
 * What {@link OpenInstancesGaugeSampler} flushes a batch of buffered {@link GaugeSample}s to.
 * {@link OpenInstancesGaugeWriter} is the production implementation (one Parquet file plus one
 * Iceberg {@code AppendFiles} commit per call); tests substitute a fake collecting implementation
 * so the sampler's batching logic can be verified without any Iceberg/DuckDB machinery.
 */
@FunctionalInterface
public interface GaugeBatchSink {

  /**
   * Durably writes every sample in {@code batch}. Never called with an empty list — {@link
   * OpenInstancesGaugeSampler} only calls this when it has at least one buffered sample.
   */
  void writeBatch(List<GaugeSample> batch);
}

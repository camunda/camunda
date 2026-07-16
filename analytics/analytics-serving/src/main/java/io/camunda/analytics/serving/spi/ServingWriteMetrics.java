/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

/**
 * The "is serving healthy" write-path signals a {@link VersionedDatasetWriter} backend reports:
 * successful upserts, version-fence rejections (the fence working, not an error — see {@link
 * VersionedDatasetWriter#fencedWrites}), the wall time of a flush/bulk call, and — where the
 * backend batches (Elasticsearch/OpenSearch bulk requests) — the batch size distribution.
 *
 * <p>A plain facade, like {@link DatasetWriter}: this module is deliberately backend- and
 * metrics-library-neutral. One instance is already bound to its backend's identity (e.g. {@code
 * "rdbms"}, {@code "elasticsearch"}, {@code "opensearch"}) by whichever wiring layer constructs the
 * Micrometer-backed implementation, so these methods never take a backend argument.
 */
public interface ServingWriteMetrics {

  /** The no-op used when the caller wires no instrumentation. */
  ServingWriteMetrics NOOP =
      new ServingWriteMetrics() {
        @Override
        public void rowWritten(final String datasetName) {}

        @Override
        public void fencedRejected() {}

        @Override
        public void writeDuration(final long durationNanos) {}

        @Override
        public void batchSize(final int size) {}
      };

  /** One row (cell/snapshot/table row) was successfully upserted for {@code datasetName}. */
  void rowWritten(String datasetName);

  /** One write was rejected by the version fence — the fence working, not an error. */
  void fencedRejected();

  /** One flush/bulk call to the backend took this long. */
  void writeDuration(long durationNanos);

  /** One batch's item count, for backends that batch (e.g. Elasticsearch/OpenSearch bulk). */
  default void batchSize(final int size) {}
}

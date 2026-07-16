/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.store;

import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.serving.spi.ServingWriteMetrics;

/**
 * One selected serving backend, resolved once at start-up by {@link AnalyticsBackends}: the single
 * control-plane {@link MetadataStore} for the stage and a factory for the per-task {@link
 * DatasetStore}s (each stage runs one task per partition, and a task owns its own store — its own
 * connection/client — so it can commit independently).
 */
public interface AnalyticsBackend {

  /** The backend's identity for observability tags: {@code rdbms|elasticsearch|opensearch}. */
  String name();

  /** The shared control-plane store (specs + meter ids) for the stage. */
  MetadataStore metadataStore();

  /** A fresh serving store for one task (its own connection/client), uninstrumented. */
  default DatasetStore newDatasetStore() {
    return newDatasetStore(ServingWriteMetrics.NOOP);
  }

  /**
   * A fresh serving store for one task whose writer reports its write-path health through {@code
   * metrics} (rows written, fence rejections, flush duration, bulk batch size). Several tasks may
   * share one {@code metrics} instance — implementations must be thread-safe for recording.
   */
  DatasetStore newDatasetStore(ServingWriteMetrics metrics);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.store;

import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.MetadataStore;

/**
 * One selected serving backend, resolved once at start-up by {@link AnalyticsBackends}: the single
 * control-plane {@link MetadataStore} for the stage and a factory for the per-shard {@link
 * DatasetStore}s (each stage runs one shard per partition, and a shard owns its own store — its own
 * connection/client — so it can commit independently).
 */
public interface AnalyticsBackend {

  /** The shared control-plane store (specs + meter ids) for the stage. */
  MetadataStore metadataStore();

  /** A fresh serving store for one shard (its own connection/client). */
  DatasetStore newDatasetStore();
}

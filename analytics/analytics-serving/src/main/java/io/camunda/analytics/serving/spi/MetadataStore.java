/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.meter.MeterIdStore;

/**
 * One backend's <b>metadata plane</b>: the durable home of the control-plane state, bundling its
 * seams the way {@link DatasetStore} bundles the serving seams. {@link #migrate()} provisions the
 * fixed metadata schema; {@link #meterIdStore()} and {@link #datasetSpecStore()} expose the stable
 * {@code aggId} allocations and the dataset specs. A backend module provides one implementation;
 * selection lives in the wiring layer, keeping this engine module backend-neutral (no {@code
 * DataSource} or SQL leaks through the SPI).
 */
public interface MetadataStore extends AutoCloseable {

  /** Provisions/upgrades the metadata schema (idempotent). */
  void migrate();

  MeterIdStore meterIdStore();

  DatasetSpecStore datasetSpecStore();

  @Override
  void close();
}

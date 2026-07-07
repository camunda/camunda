/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.dataset.RegisteredDataset;
import java.util.List;
import java.util.Optional;

/**
 * The backend-neutral control-plane store of dataset specs — the source of truth for which datasets
 * exist, supporting <b>create</b>, <b>read</b>, and <b>search</b>. The control plane creates
 * declarations here; both stages read/search them and compile them, so a dataset change is a data
 * change, not a redeploy. A backend module implements this over RDBMS (normalized rows), ES, or OS
 * (a spec document); callers depend on the interface and the neutral {@link RegisteredDataset} /
 * {@link DatasetSpecQuery}, never on SQL or a client.
 */
public interface DatasetSpecStore {

  /** Whether any dataset spec is stored (drives the idempotent bootstrap). */
  boolean isEmpty();

  /**
   * The number of stored specs — a cheap change probe. Specs are create-only through this SPI, so
   * the count moves if and only if the set of datasets changed; a catalog refresh checks it before
   * paying for a full load + recompile.
   */
  long specCount();

  /** Persists one dataset spec (its declaration, activation vector, and schema version). */
  void create(RegisteredDataset spec);

  /** Reads a single spec by its {@code cubeId}, or empty if absent. */
  Optional<RegisteredDataset> read(long cubeId);

  /**
   * Returns the specs matching {@code query}; {@link DatasetSpecQuery#all()} returns every spec.
   */
  List<RegisteredDataset> search(DatasetSpecQuery query);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.RegisteredDataset;
import java.util.List;

/**
 * The backend-neutral persistence of dataset specs — the metadata plane's source of truth for which
 * datasets exist. The control plane bootstraps declarations here once; both stages reload the full
 * {@link RegisteredDataset}s and compile them, so a dataset change is a data change, not a
 * redeploy. A backend module implements this (RDBMS as normalized columns; an index for ES/OS);
 * callers depend on the interface, never on a {@code DataSource} or SQL.
 */
public interface DatasetSpecStore {

  /** Whether any dataset spec is stored (drives the idempotent bootstrap). */
  boolean isEmpty();

  /** Persists one registered dataset (its declaration, activation vector, and schema version). */
  void save(RegisteredDataset registered);

  /** Reloads every stored spec. */
  List<RegisteredDataset> loadAll();
}

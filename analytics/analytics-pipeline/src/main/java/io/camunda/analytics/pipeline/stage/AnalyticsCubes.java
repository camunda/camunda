/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.ActiveTable;
import io.camunda.analytics.dataset.store.MetadataStore;
import io.camunda.analytics.dataset.store.StandardDatasets;
import java.util.List;

/**
 * The stages' view of the standard datasets: a thin delegate to {@link StandardDatasets}, the
 * shared source of truth in the serving layer (so the pipeline and the read layer agree on the
 * declarations and their ids). Both stages bootstrap the metadata plane once (idempotent) and
 * reload the compiled cubes/tables it holds.
 */
public final class AnalyticsCubes {

  private AnalyticsCubes() {}

  public static void bootstrap(final MetadataStore metadataStore) {
    StandardDatasets.bootstrap(metadataStore);
  }

  public static List<ActiveCube> loadCubes(final MetadataStore metadataStore) {
    return StandardDatasets.loadCubes(metadataStore);
  }

  public static List<ActiveTable> loadTables(final MetadataStore metadataStore) {
    return StandardDatasets.loadTables(metadataStore);
  }
}

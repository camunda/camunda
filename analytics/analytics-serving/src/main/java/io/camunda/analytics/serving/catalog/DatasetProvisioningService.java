/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.MetadataStore;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The single admission path for a dataset — the control-plane operation that turns a {@link
 * DatasetDeclaration} into a live cube: compile it (validating it and, as a side effect, allocating
 * and persisting stable {@code aggId}s), freeze its forward-only activation cutover, persist the
 * spec, and provision its serving structure. Both a runtime "define a dataset" request and the
 * standard-dashboard bootstrap go through here, so there is one way a dataset is admitted, not two.
 *
 * <p><b>Activation.</b> A runtime-declared dataset activates at {@code now + debounce} (event-time
 * cutover; see ADR 0005) — the debounce window gives the running stages time to pick up the new
 * cube before any fact is due, so no data is collected before the pipeline is ready. Admission is
 * single-threaded ({@code synchronized}) because it derives the next {@code cubeId} from the
 * current spec set; concurrent admissions would otherwise race on the id.
 *
 * <p>TODO(analytics): {@code StandardDatasets.bootstrap} should route through {@link
 * #bootstrapStandardDatasets()} once the pipeline stages supply a {@link DatasetSchemaManager} at
 * bootstrap, retiring the separate bootstrap in {@code StandardDatasets}.
 */
public final class DatasetProvisioningService {

  private final MetadataStore metadataStore;
  private final DatasetSchemaManager schemaManager;
  private final MeterCatalog meterCatalog;
  private final LongSupplier clock;
  private final long activationDebounceMs;

  public DatasetProvisioningService(
      final MetadataStore metadataStore,
      final DatasetSchemaManager schemaManager,
      final MeterCatalog meterCatalog,
      final LongSupplier clock,
      final long activationDebounceMs) {
    this.metadataStore = metadataStore;
    this.schemaManager = schemaManager;
    this.meterCatalog = meterCatalog;
    this.clock = clock;
    this.activationDebounceMs = activationDebounceMs;
  }

  /**
   * Admits a runtime-declared dataset: forward-only from {@code now + debounce}. Returns the
   * registered dataset (its assigned {@code cubeId} and frozen activation).
   */
  public synchronized RegisteredDataset provision(final DatasetDeclaration declaration) {
    return admit(declaration, clock.getAsLong() + activationDebounceMs);
  }

  /**
   * Idempotently admits the standard dashboards (from the beginning of history). A no-op once any
   * dataset exists, so first run wins and later starts reuse the persisted ids.
   */
  public synchronized void bootstrapStandardDatasets() {
    if (!metadataStore.datasetSpecStore().isEmpty()) {
      return;
    }
    for (final DatasetDeclaration declaration : StandardDatasets.declarations()) {
      admit(declaration, 0L);
    }
    for (final DatasetDeclaration declaration : StandardDatasets.tableDeclarations()) {
      admit(declaration, 0L);
    }
  }

  private RegisteredDataset admit(
      final DatasetDeclaration declaration, final long activationTimestampMs) {
    // Continue cube ids past the persisted specs (empty position vector: the event-time cutover is
    // the forward-only gate today — see RegisteredDataset).
    final DatasetRegistry registry =
        DatasetRegistry.restore(metadataStore.datasetSpecStore().search(DatasetSpecQuery.all()));
    final DatasetCompiler compiler =
        new DatasetCompiler(meterCatalog, new MeterIdRegistry(metadataStore.meterIdStore()));
    final RegisteredDataset registered =
        registry.admit(declaration, Map.of(), activationTimestampMs);
    metadataStore.datasetSpecStore().create(registered);
    if (declaration.kind() == DatasetKind.TABLE) {
      // compileTable carries no aggIds; provision the projected-row structure.
      schemaManager.ensureTable(compiler.compileTable(registered.cubeId(), declaration));
    } else {
      // compile allocates + persists this cube's aggIds, then provision the cell structure.
      schemaManager.ensure(compiler.compile(registered.cubeId(), declaration));
    }
    return registered;
  }
}

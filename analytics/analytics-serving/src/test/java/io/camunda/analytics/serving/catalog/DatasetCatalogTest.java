/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.ActiveCube;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import org.junit.jupiter.api.Test;

final class DatasetCatalogTest {

  private final InMemoryMetadataStore metadataStore = new InMemoryMetadataStore();
  private final DatasetProvisioningService service =
      new DatasetProvisioningService(
          metadataStore,
          new NoopSchemaManager(),
          MeterCatalog.withDefaults(),
          () -> 1_000L,
          30_000L);

  private static DatasetDeclaration cube(final String name) {
    return DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
        .dimension("bpmnProcessId", DimensionType.STRING)
        .meter(Meter.of("count", MeterCatalog.COUNT))
        .window(60_000L)
        .build();
  }

  @Test
  void shouldStartEmptyAtVersionZero() {
    // given an empty metadata plane
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);

    // then the catalog is empty at version 0
    assertThat(catalog.version()).isZero();
    assertThat(catalog.cubes()).isEmpty();
    assertThat(catalog.tables()).isEmpty();
  }

  @Test
  void shouldBumpVersionAndExposeADatasetAfterProvisionAndRefresh() {
    // given a live catalog over an empty plane
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);

    // when a dataset is provisioned and the catalog refreshed
    service.provision(cube("throughput"));
    catalog.refresh();

    // then the catalog exposes it at a bumped version
    assertThat(catalog.version()).isEqualTo(1L);
    assertThat(catalog.cubes()).extracting(c -> c.compiled().name()).containsExactly("throughput");
  }

  @Test
  void shouldNotBumpVersionOnRedundantRefresh() {
    // given a catalog that already saw one dataset
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);
    service.provision(cube("throughput"));
    catalog.refresh();
    final long afterFirst = catalog.version();

    // when refreshed again with no change
    catalog.refresh();

    // then the version is unchanged (no needless rebuild is triggered)
    assertThat(catalog.version()).isEqualTo(afterFirst);
  }

  @Test
  void shouldBumpAgainWhenAnotherDatasetIsProvisioned() {
    // given a catalog with one dataset applied
    final DatasetCatalog catalog = new DatasetCatalog(metadataStore);
    service.provision(cube("a"));
    catalog.refresh();

    // when a second dataset is provisioned and the catalog refreshed
    service.provision(cube("b"));
    catalog.refresh();

    // then the version advances and both datasets are exposed
    assertThat(catalog.version()).isEqualTo(2L);
    assertThat(catalog.cubes()).extracting(ActiveCube::registered).hasSize(2);
  }

  private static final class NoopSchemaManager implements DatasetSchemaManager {
    @Override
    public void ensure(final CompiledDataset dataset) {}

    @Override
    public void ensureTable(final CompiledTable table) {}
  }
}

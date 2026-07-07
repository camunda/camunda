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
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdStore;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.DatasetSpecStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.serving.spi.ReportSpecStore;
import java.util.List;
import java.util.Optional;
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

  @Test
  void shouldNotLoadOrRecompileOnANoChangeRefresh() {
    // given a catalog that already applied one dataset, over a store that counts spec loads
    final CountingMetadataStore counting = new CountingMetadataStore(metadataStore);
    final DatasetCatalog catalog = new DatasetCatalog(counting);
    service.provision(cube("throughput"));
    catalog.refresh();
    final int loadsAfterChange = counting.searches;

    // when refreshed redundantly (the steady-state reload check)
    catalog.refresh();
    catalog.refresh();

    // then only the cheap count probe ran — the specs were not re-loaded (hence not re-compiled)
    assertThat(counting.searches).isEqualTo(loadsAfterChange);
    assertThat(counting.countProbes).isGreaterThanOrEqualTo(2);
  }

  /** Delegates to the in-memory store, counting spec loads and count probes. */
  private static final class CountingMetadataStore implements MetadataStore {

    private final MetadataStore delegate;
    private int searches;
    private int countProbes;

    private CountingMetadataStore(final MetadataStore delegate) {
      this.delegate = delegate;
    }

    @Override
    public void migrate() {}

    @Override
    public MeterIdStore meterIdStore() {
      return delegate.meterIdStore();
    }

    @Override
    public DatasetSpecStore datasetSpecStore() {
      final DatasetSpecStore inner = delegate.datasetSpecStore();
      return new DatasetSpecStore() {
        @Override
        public boolean isEmpty() {
          return inner.isEmpty();
        }

        @Override
        public long specCount() {
          countProbes++;
          return inner.specCount();
        }

        @Override
        public void create(final RegisteredDataset spec) {
          inner.create(spec);
        }

        @Override
        public Optional<RegisteredDataset> read(final long cubeId) {
          return inner.read(cubeId);
        }

        @Override
        public List<RegisteredDataset> search(final DatasetSpecQuery query) {
          searches++;
          return inner.search(query);
        }
      };
    }

    @Override
    public ReportSpecStore reportSpecStore() {
      return delegate.reportSpecStore();
    }

    @Override
    public void close() {}
  }

  private static final class NoopSchemaManager implements DatasetSchemaManager {
    @Override
    public void ensure(final CompiledDataset dataset) {}

    @Override
    public void ensureTable(final CompiledTable table) {}
  }
}

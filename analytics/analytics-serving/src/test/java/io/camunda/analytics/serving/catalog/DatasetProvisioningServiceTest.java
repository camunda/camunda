/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class DatasetProvisioningServiceTest {

  private static final long DEBOUNCE_MS = 10_000L;

  private final InMemoryMetadataStore metadataStore = new InMemoryMetadataStore();
  private final RecordingSchemaManager schemaManager = new RecordingSchemaManager();

  private DatasetProvisioningService serviceAt(final long nowMs) {
    return new DatasetProvisioningService(
        metadataStore, schemaManager, MeterCatalog.withDefaults(), () -> nowMs, DEBOUNCE_MS);
  }

  private static DatasetDeclaration cube(final String name) {
    return DatasetDeclaration.builder(name, FactType.PROCESS_INSTANCE)
        .dimension("bpmnProcessId", DimensionType.STRING)
        .meter(Meter.of("count", MeterCatalog.COUNT))
        .window(60_000L)
        .build();
  }

  @Test
  void shouldProvisionAggregatedDatasetForwardOnlyFromNowPlusDebounce() {
    // given
    final DatasetProvisioningService service = serviceAt(1_000L);

    // when
    final RegisteredDataset registered = service.provision(cube("throughput"));

    // then the activation cutover is frozen at now + debounce (forward-only, event-time)
    assertThat(registered.activationTimestampMs()).isEqualTo(11_000L);
    assertThat(registered.cubeId()).isEqualTo(1L);
    // and the spec is persisted
    assertThat(metadataStore.datasetSpecStore().read(1L)).contains(registered);
    // and the cube's aggId was allocated + persisted (compile side effect)
    assertThat(metadataStore.meterIdStore().load()).isNotEmpty();
    // and its serving schema was provisioned
    assertThat(schemaManager.ensuredCubes).extracting(CompiledDataset::cubeId).containsExactly(1L);
    assertThat(schemaManager.ensuredTables).isEmpty();
  }

  @Test
  void shouldContinueCubeIdsPastExistingSpecs() {
    // given one already provisioned
    serviceAt(1_000L).provision(cube("a"));

    // when a second is provisioned
    final RegisteredDataset second = serviceAt(2_000L).provision(cube("b"));

    // then the cube id continues (no reuse) and activation reflects the second call's clock
    assertThat(second.cubeId()).isEqualTo(2L);
    assertThat(second.activationTimestampMs()).isEqualTo(12_000L);
  }

  @Test
  void shouldProvisionTableDatasetViaEnsureTable() {
    // given a projected (raw) table declaration
    final DatasetDeclaration table =
        DatasetDeclaration.builder("raw", FactType.PROCESS_INSTANCE)
            .asTable("processInstanceKey")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .build();

    // when
    final RegisteredDataset registered = serviceAt(1_000L).provision(table);

    // then the table structure is provisioned (no cube, no meters allocated)
    assertThat(schemaManager.ensuredTables).extracting(CompiledTable::cubeId).containsExactly(1L);
    assertThat(schemaManager.ensuredCubes).isEmpty();
    assertThat(metadataStore.meterIdStore().load()).isEmpty();
    assertThat(registered.activationTimestampMs()).isEqualTo(11_000L);
  }

  @Test
  void shouldBootstrapStandardDatasetsFromHistoryIdempotently() {
    // given a fresh store
    final DatasetProvisioningService service = serviceAt(9_999L);

    // when bootstrapped
    service.bootstrapStandardDatasets();
    final int afterFirst = metadataStore.datasetSpecStore().search(DatasetSpecQuery.all()).size();

    // then every standard dataset is admitted from the beginning of history (cutover 0)
    final int expected =
        StandardDatasets.declarations().size() + StandardDatasets.tableDeclarations().size();
    assertThat(afterFirst).isEqualTo(expected);
    assertThat(metadataStore.datasetSpecStore().search(DatasetSpecQuery.all()))
        .allMatch(spec -> spec.activationTimestampMs() == 0L);

    // and a second bootstrap is a no-op (first run wins)
    service.bootstrapStandardDatasets();
    assertThat(metadataStore.datasetSpecStore().search(DatasetSpecQuery.all())).hasSize(expected);
  }

  // --- in-memory SPI doubles ({@link InMemoryMetadataStore} is shared) -------------------------

  private static final class RecordingSchemaManager implements DatasetSchemaManager {
    private final List<CompiledDataset> ensuredCubes = new ArrayList<>();
    private final List<CompiledTable> ensuredTables = new ArrayList<>();

    @Override
    public void ensure(final CompiledDataset dataset) {
      ensuredCubes.add(dataset);
    }

    @Override
    public void ensureTable(final CompiledTable table) {
      ensuredTables.add(table);
    }
  }
}

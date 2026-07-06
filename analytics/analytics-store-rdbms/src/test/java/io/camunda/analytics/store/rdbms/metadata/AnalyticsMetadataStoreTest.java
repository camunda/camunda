/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterKey;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.MetadataStore;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class AnalyticsMetadataStoreTest {

  private final JdbcDataSource dataSource = h2();
  private final MetadataStore store = new RdbmsMetadataStore(dataSource);

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @BeforeEach
  void migrate() {
    store.migrate();
  }

  @Test
  void shouldMigrateIdempotently() {
    // when the changelog is applied a second time
    store.migrate();

    // then it is a no-op and the store is usable
    assertThat(store.datasetSpecStore().isEmpty()).isTrue();
  }

  @Test
  void shouldRoundTripNormalizedDatasetSpecs() {
    // given an aggregated cube (filter + meter with params + window + activation) and a table
    final DatasetRegistry registry = new DatasetRegistry();
    final RegisteredDataset sla =
        registry.admit(
            DatasetDeclaration.builder("process-sla", FactType.PROCESS_INSTANCE)
                .filterEquals("transition", "COMPLETED")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(
                    new Meter(
                        "sla", "ratio", "durationMs", Map.of("op", "le", "threshold", "300000")))
                .window(60_000L)
                .build(),
            Map.of(0, 100L, 1, 250L),
            1_700_000_000_000L);
    final RegisteredDataset raw =
        registry.admit(
            DatasetDeclaration.builder("raw-instances", FactType.PROCESS_INSTANCE)
                .asTable("processInstanceKey")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("durationMs", DimensionType.LONG)
                .build(),
            Map.of(),
            0L);

    // when persisted
    assertThat(store.datasetSpecStore().isEmpty()).isTrue();
    store.datasetSpecStore().create(sla);
    store.datasetSpecStore().create(raw);

    // then create/read/search all round-trip the reconstructed specs (declaration + activation +
    // ids)
    assertThat(store.datasetSpecStore().isEmpty()).isFalse();
    assertThat(store.datasetSpecStore().search(DatasetSpecQuery.all()))
        .containsExactlyInAnyOrder(sla, raw);
    assertThat(store.datasetSpecStore().read(sla.cubeId())).contains(sla);
    assertThat(store.datasetSpecStore().read(999L)).isEmpty();
    assertThat(store.datasetSpecStore().search(DatasetSpecQuery.byName("raw-instances")))
        .containsExactly(raw);
    assertThat(store.datasetSpecStore().search(DatasetSpecQuery.byKind(DatasetKind.TABLE)))
        .containsExactly(raw);
  }

  @Test
  void shouldRoundTripMeterIds() {
    // given persisted aggId allocations
    store.meterIdStore().persist(new MeterKey(1L, "count@60000"), 1);
    store.meterIdStore().persist(new MeterKey(1L, "p95@60000"), 2);
    store.meterIdStore().persist(new MeterKey(2L, "count@60000"), 3);

    // when reloaded (a restart) then every allocation is restored
    assertThat(store.meterIdStore().load())
        .containsOnly(
            entry(new MeterKey(1L, "count@60000"), 1),
            entry(new MeterKey(1L, "p95@60000"), 2),
            entry(new MeterKey(2L, "count@60000"), 3));
  }
}

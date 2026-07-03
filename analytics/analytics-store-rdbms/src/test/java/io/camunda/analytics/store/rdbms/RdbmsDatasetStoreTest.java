/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.CompiledProjection;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.store.DatasetQueryExecutor;
import io.camunda.analytics.dataset.store.DatasetQueryPlanner;
import io.camunda.analytics.dataset.store.ReportQuery;
import io.camunda.analytics.dataset.store.ReportResult;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class RdbmsDatasetStoreTest {

  private static final long MINUTE = 60_000L;

  private final JdbcDataSource dataSource = h2();
  private final RdbmsDatasetStore store = new RdbmsDatasetStore(dataSource);
  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(MINUTE)
                  .build());

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldDetectH2Dialect() {
    assertThat(store.dialect()).isEqualTo(RdbmsDialect.H2);
  }

  @Test
  void shouldAcceptSafeIdentifiersAndRejectInjectionAttempts() {
    // given — legitimate declared names, including a namespaced variable dimension
    assertThat(RdbmsNames.column("bpmnProcessId")).isEqualTo("bpmnProcessId");
    assertThat(RdbmsNames.column("var.region")).isEqualTo("var_region");

    // then — anything carrying SQL metacharacters is rejected, not coerced into valid SQL
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> RdbmsNames.column("count\"; DROP TABLE dataset_1; --"))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> RdbmsNames.column("a b"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldWriteCellsAndServeThemThroughTheExecutor() {
    // given a provisioned cube with per-window counts written idempotently
    store.schemaManager().ensure(dataset);
    store.writer().upsertCell(dataset, key("orders"), 0L, MINUTE, "count", count(3L));
    store.writer().upsertCell(dataset, key("orders"), MINUTE, MINUTE, "count", count(2L));
    store.writer().upsertCell(dataset, key("ship"), 0L, MINUTE, "count", count(4L));
    store.writer().upsertCell(dataset, key("orders"), 0L, MINUTE, "count", count(3L)); // replay
    store.writer().flush();

    // when read back through the neutral planner + executor over the whole range in one bucket
    final DatasetQueryExecutor executor =
        new DatasetQueryExecutor(new DatasetQueryPlanner(), store.queryClient());
    final ReportResult result =
        executor.execute(
            new ReportQuery(
                List.of("bpmnProcessId"), 0L, 2 * MINUTE, 2 * MINUTE, List.of(), List.of("count")),
            dataset);

    // then windows merge and the replay did not double-count: orders = 5, ship = 4
    assertThat(result.rows())
        .extracting(r -> r.dimensions().get("bpmnProcessId"), r -> r.measures().get("count"))
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 5L), Tuple.tuple("ship", 4L));
  }

  @Test
  void shouldProvisionAndUpsertProjectedRowsIdempotently() {
    // given a projected dataset table
    final CompiledProjection projection =
        new DatasetCompiler(
                MeterCatalog.withDefaults(), new MeterRegistry(new InMemoryMeterIdStore()))
            .compileProjection(
                7L,
                DatasetDeclaration.builder("raw-instances", FactType.PROCESS_INSTANCE)
                    .projectedBy("processInstanceKey")
                    .dimension("bpmnProcessId", DimensionType.STRING)
                    .dimension("durationMs", DimensionType.LONG)
                    .build());
    store.schemaManager().ensureProjection(projection);

    // when two rows are written and one is replayed
    store.writer().upsertRow(projection, "1001", List.of("order", 1_500L));
    store.writer().upsertRow(projection, "1002", List.of("ship", 42_000L));
    store.writer().upsertRow(projection, "1001", List.of("order", 1_500L));
    store.writer().flush();

    // then there are two distinct rows
    assertThat(projectionRowCount()).isEqualTo(2);
  }

  private DimensionKey key(final String process) {
    return DimensionKey.of(dataset.grain(), process);
  }

  @SuppressWarnings("unchecked")
  private byte[] count(final long value) {
    final CompiledMeter meter = dataset.meters().get(0);
    return ((BoundMeter<Object, Object>) meter.bound()).accumulatorCodec().toBytes(value);
  }

  private int projectionRowCount() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(*) FROM projection_7");
        ResultSet rs = statement.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

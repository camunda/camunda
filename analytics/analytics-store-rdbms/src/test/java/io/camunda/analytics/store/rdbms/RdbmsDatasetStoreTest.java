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
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.query.DatasetQueryExecutor;
import io.camunda.analytics.query.DatasetQueryPlanner;
import io.camunda.analytics.query.ReportQuery;
import io.camunda.analytics.query.ReportResult;
import io.camunda.analytics.query.TableQuery;
import io.camunda.analytics.query.TableQueryExecutor;
import io.camunda.analytics.serving.spi.Cell;
import io.camunda.analytics.serving.spi.DatasetFetch;
import io.camunda.analytics.serving.spi.TableRow;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.agrona.collections.MutableLong;
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
              MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
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
    // given — legitimate declared names get a trailing underscore (never a reserved word), and a
    // namespaced variable dimension folds its dot to an underscore before the suffix
    assertThat(RdbmsNames.column("bpmnProcessId")).isEqualTo("bpmnProcessId_");
    assertThat(RdbmsNames.column("var.region")).isEqualTo("var_region_");

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
    store.writer().upsertCell(dataset, key("orders"), 0L, MINUTE, count(3L), WriteVersion.SEED);
    store.writer().upsertCell(dataset, key("orders"), MINUTE, MINUTE, count(2L), WriteVersion.SEED);
    store.writer().upsertCell(dataset, key("ship"), 0L, MINUTE, count(4L), WriteVersion.SEED);
    store
        .writer()
        .upsertCell(dataset, key("orders"), 0L, MINUTE, count(3L), WriteVersion.SEED); // replay
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
  void shouldStreamRawCellsThroughACursor() {
    // given cells across two windows
    store.schemaManager().ensure(dataset);
    store.writer().upsertCell(dataset, key("orders"), 0L, MINUTE, count(3L), WriteVersion.SEED);
    store.writer().upsertCell(dataset, key("orders"), MINUTE, MINUTE, count(2L), WriteVersion.SEED);
    store.writer().flush();

    // when streamed through the forward cursor (never materialized as a List)
    final List<Cell> cells = new ArrayList<>();
    store
        .queryClient()
        .streamCells(
            new DatasetFetch(dataset, MINUTE, 0L, 2 * MINUTE, List.of(), List.of("count")),
            cells::add);

    // then both cells arrive with their grain key and window (accumulators still encoded)
    assertThat(cells)
        .extracting(c -> c.key().get("bpmnProcessId"), Cell::windowStart)
        .containsExactlyInAnyOrder(Tuple.tuple("orders", 0L), Tuple.tuple("orders", MINUTE));
  }

  @Test
  void shouldProvisionAndUpsertTableRowsIdempotently() {
    // given a raw table
    final CompiledTable table = rawInstancesTable();
    store.schemaManager().ensureTable(table);

    // when two rows are written and one is replayed
    store.writer().upsertRow(table, "1001", List.of("order", 1_500L), WriteVersion.SEED);
    store.writer().upsertRow(table, "1002", List.of("ship", 42_000L), WriteVersion.SEED);
    store.writer().upsertRow(table, "1001", List.of("order", 1_500L), WriteVersion.SEED);
    store.writer().flush();

    // then there are two distinct rows
    assertThat(tableRowCount()).isEqualTo(2);
  }

  @Test
  void shouldFetchTableRowsBackThroughTheQueryClient() {
    // given a table with two written rows
    final CompiledTable table = rawInstancesTable();
    store.schemaManager().ensureTable(table);
    store.writer().upsertRow(table, "1001", List.of("order", 1_500L), WriteVersion.SEED);
    store.writer().upsertRow(table, "1002", List.of("ship", 42_000L), WriteVersion.SEED);
    store.writer().flush();
    final TableQueryExecutor executor = new TableQueryExecutor(store.queryClient());

    // when all rows are fetched, both come back with their typed column values
    assertThat(executor.execute(new TableQuery(List.of(), 100), table))
        .extracting(r -> r.values().get("bpmnProcessId"), r -> r.values().get("durationMs"))
        .containsExactlyInAnyOrder(Tuple.tuple("order", 1_500L), Tuple.tuple("ship", 42_000L));

    // when filtered by a declared column, only the matching row comes back
    assertThat(
            executor.execute(
                new TableQuery(List.of(FilterPredicate.equals("bpmnProcessId", "ship")), 100),
                table))
        .singleElement()
        .extracting(TableRow::values)
        .satisfies(
            values -> {
              assertThat(values.get("bpmnProcessId")).isEqualTo("ship");
              assertThat(values.get("durationMs")).isEqualTo(42_000L);
            });
  }

  @Test
  void shouldDeleteTableRowsByKeyIdempotently() {
    // given a table with two written rows
    final CompiledTable table = rawInstancesTable();
    store.schemaManager().ensureTable(table);
    store.writer().upsertRow(table, "1001", List.of("order", 1_500L), WriteVersion.SEED);
    store.writer().upsertRow(table, "1002", List.of("ship", 42_000L), WriteVersion.SEED);
    store.writer().flush();

    // when one row is deleted (twice — the replayed eviction is a no-op)
    store.writer().deleteRow(table, "1001", WriteVersion.SEED);
    store.writer().deleteRow(table, "1001", WriteVersion.SEED);
    store.writer().flush();

    // then only the other row remains, and the absent-row deletes were not counted as fenced
    assertThat(
            new TableQueryExecutor(store.queryClient())
                .execute(new TableQuery(List.of(), 100), table))
        .singleElement()
        .satisfies(r -> assertThat(r.values().get("bpmnProcessId")).isEqualTo("ship"));
    assertThat(store.writer().fencedWrites()).isZero();
  }

  @Test
  void shouldFenceAStaleDeleteAndOrderDeletesAfterUpsertsWithinOneFlush() {
    // given a row owned at (epoch 5, offset 100)
    final CompiledTable table = rawInstancesTable();
    store.schemaManager().ensureTable(table);
    store.writer().upsertRow(table, "1001", List.of("order", 1_500L), new WriteVersion(5, 100));
    store.writer().flush();

    // when a fenced zombie's stale delete arrives (older epoch)
    store.writer().deleteRow(table, "1001", new WriteVersion(4, 999));
    store.writer().flush();

    // then the row survives — the delete is fenced by the stored version
    assertThat(tableRowCount()).isEqualTo(1);

    // when one flush carries the upsert-then-evict of a fresh key (a short-lived instance)
    store.writer().upsertRow(table, "1002", List.of("ship", 42_000L), new WriteVersion(5, 200));
    store.writer().deleteRow(table, "1002", new WriteVersion(5, 201));
    store.writer().flush();

    // then the delete ran after the upsert: the short-lived row is gone, the owned one remains
    assertThat(
            new TableQueryExecutor(store.queryClient())
                .execute(new TableQuery(List.of(), 100), table))
        .singleElement()
        .satisfies(r -> assertThat(r.values().get("bpmnProcessId")).isEqualTo("order"));
  }

  @Test
  void shouldStoreAndReadALargeTextColumnBeyondVarcharBound() {
    // given a table with a TEXT column (CLOB/TEXT), like process definitions holding BPMN XML
    final CompiledTable table =
        new DatasetCompiler(
                MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
            .compileTable(
                8L,
                DatasetDeclaration.builder("defs", FactType.PROCESS_INSTANCE)
                    .asTable("id")
                    .dimension("id", DimensionType.STRING)
                    .dimension("xml", DimensionType.TEXT)
                    .build());
    store.schemaManager().ensureTable(table);
    final String bigXml = "<x>" + "a".repeat(6000) + "</x>"; // > the STRING VARCHAR(4000) bound

    // when a row far larger than the VARCHAR bound is written and read back
    store.writer().upsertRow(table, "1", List.of("1", bigXml), WriteVersion.SEED);
    store.writer().flush();
    final List<TableRow> rows =
        new TableQueryExecutor(store.queryClient()).execute(new TableQuery(List.of(), 10), table);

    // then the full text survives the round trip (CLOB stored + read as a String)
    assertThat(rows)
        .singleElement()
        .satisfies(r -> assertThat(r.values().get("xml")).isEqualTo(bigXml));
  }

  private static CompiledTable rawInstancesTable() {
    return new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
        .compileTable(
            7L,
            DatasetDeclaration.builder("raw-instances", FactType.PROCESS_INSTANCE)
                .asTable("processInstanceKey")
                .dimension("bpmnProcessId", DimensionType.STRING)
                .dimension("durationMs", DimensionType.LONG)
                .build());
  }

  private DimensionKey key(final String process) {
    return DimensionKey.of(dataset.grain(), process);
  }

  private byte[] count(final long value) {
    return new CompositeAccumulatorValue(dataset.meterBounds())
        .toBytes(new Object[] {new MutableLong(value)});
  }

  private int tableRowCount() {
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.catalog.LakeCommitCoordinator.CommitAllResult;
import io.camunda.analytics.lake.catalog.LakeCommitCoordinator.TableChange;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.FileFormat;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.apache.iceberg.types.Types.LongType;
import org.apache.iceberg.types.Types.NestedField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LakeCommitCoordinatorTest {

  private static final Namespace NAMESPACE = Namespace.of("lake");
  private static final TableIdentifier RAW = TableIdentifier.of(NAMESPACE, "raw");
  private static final TableIdentifier METRICS = TableIdentifier.of(NAMESPACE, "raw_metrics");
  private static final Schema SCHEMA = new Schema(NestedField.required(1, "value", LongType.get()));

  @TempDir private Path warehouse;

  private JdbcCatalog catalog;
  private String jdbcUrl;
  private Table raw;
  private Table metrics;
  private LakeCommitCoordinator coordinator;

  @BeforeEach
  void setUp() {
    jdbcUrl = "jdbc:h2:file:" + warehouse.toAbsolutePath().resolve("catalog");
    catalog = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
    catalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI,
            jdbcUrl,
            CatalogProperties.WAREHOUSE_LOCATION,
            stripTrailingSlash(warehouse.toAbsolutePath().toUri().toString())));
    catalog.createNamespace(NAMESPACE);
    raw = catalog.createTable(RAW, SCHEMA, PartitionSpec.unpartitioned());
    metrics = catalog.createTable(METRICS, SCHEMA, PartitionSpec.unpartitioned());
    coordinator = new LakeCommitCoordinator(jdbcUrl, "lake");
  }

  @AfterEach
  void tearDown() {
    catalog.close();
  }

  @Test
  void shouldCommitAllTablesInOneBatch() {
    // given
    final List<TableChange> changes =
        List.of(
            new TableChange(
                RAW,
                raw,
                table -> table.newAppend().appendFile(dataFile("raw-1")).set("k", "v1").commit()),
            new TableChange(
                METRICS,
                metrics,
                table ->
                    table.newAppend().appendFile(dataFile("metrics-1")).set("k", "v2").commit()));

    // when
    final CommitAllResult result =
        coordinator.commitAll(changes, Map.of("lake.offset.p1", "815", "lake.zbpos.z1", "4714"));

    // then
    assertThat(result.journalId()).isPositive();
    assertThat(result.commits()).hasSize(2);
    assertThat(catalog.loadTable(RAW).currentSnapshot().summary()).containsEntry("k", "v1");
    assertThat(catalog.loadTable(METRICS).currentSnapshot().summary()).containsEntry("k", "v2");
    assertThat(journalRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).contains("lake.raw@", "lake.raw_metrics@");
              assertThat(row).contains("lake.offset.p1=815", "lake.zbpos.z1=4714");
            });
  }

  @Test
  void shouldRetryWholeBatchWhenOneSwapLosesItsRace() {
    // given -- while the batch is staging, an interloper commits directly to the raw table (the
    // compactor's shape), invalidating the already-staged swap predicate exactly once
    final AtomicBoolean interloped = new AtomicBoolean();
    final List<TableChange> changes =
        List.of(
            new TableChange(
                RAW, raw, table -> table.newAppend().appendFile(dataFile("raw-batch")).commit()),
            new TableChange(
                METRICS,
                metrics,
                table -> {
                  if (interloped.compareAndSet(false, true)) {
                    catalog
                        .loadTable(RAW)
                        .newAppend()
                        .appendFile(dataFile("raw-interloper"))
                        .commit();
                  }
                  table.newAppend().appendFile(dataFile("metrics-batch")).commit();
                }));

    // when
    final CommitAllResult result = coordinator.commitAll(changes, Map.of());

    // then -- the second attempt won, on top of the interloper's commit, and exactly one batch
    // was journaled; the first attempt's staged metadata files are queued for sweep
    assertThat(result.journalId()).isPositive();
    assertThat(catalog.loadTable(RAW).snapshots()).hasSize(2);
    assertThat(catalog.loadTable(METRICS).snapshots()).hasSize(1);
    assertThat(journalRows()).hasSize(1);
    assertThat(pendingDeletePaths())
        .hasSize(2)
        .allSatisfy(path -> assertThat(path).contains(".metadata.json"));
  }

  @Test
  void shouldGiveUpAfterRepeatedRaceLosses() {
    // given -- an interloper that wins on every staging attempt
    final List<TableChange> changes =
        List.of(
            new TableChange(
                RAW, raw, table -> table.newAppend().appendFile(dataFile("raw-batch")).commit()),
            new TableChange(
                METRICS,
                metrics,
                table -> {
                  catalog
                      .loadTable(RAW)
                      .newAppend()
                      .appendFile(dataFile("raw-interloper"))
                      .commit();
                  table.newAppend().appendFile(dataFile("metrics-batch")).commit();
                }));

    // when / then
    assertThatThrownBy(() -> coordinator.commitAll(changes, Map.of()))
        .isInstanceOf(CommitFailedException.class)
        .hasMessageContaining("gave up");
    assertThat(journalRows()).isEmpty();
    assertThat(catalog.loadTable(METRICS).snapshots()).isEmpty();
  }

  @Test
  void shouldSkipNoOpChanges() {
    // given
    final List<TableChange> changes = List.of(new TableChange(RAW, raw, table -> {}));

    // when
    final CommitAllResult result = coordinator.commitAll(changes, Map.of());

    // then
    assertThat(result.journalId()).isEqualTo(-1);
    assertThat(result.commits()).isEmpty();
    assertThat(journalRows()).isEmpty();
    assertThat(catalog.loadTable(RAW).currentSnapshot()).isNull();
  }

  @Test
  void shouldSweepPendingDeletes() throws Exception {
    // given -- a queued orphan that really exists on disk
    final Path orphan = warehouse.resolve("orphan.metadata.json");
    Files.writeString(orphan, "{}");
    try (final Connection connection = DriverManager.getConnection(jdbcUrl);
        final Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO lake_pending_deletes (path, reason) VALUES ('"
              + orphan.toAbsolutePath()
              + "', 'test')");
    }

    // when
    final int deleted;
    try (final LocalFileIO io = new LocalFileIO()) {
      deleted = coordinator.sweepPendingDeletes(io);

      // then
      assertThat(deleted).isEqualTo(1);
      assertThat(Files.exists(orphan)).isFalse();
      assertThat(pendingDeletePaths()).isEmpty();
      assertThat(coordinator.sweepPendingDeletes(io)).isZero();
    }
  }

  private DataFile dataFile(final String name) {
    return DataFiles.builder(PartitionSpec.unpartitioned())
        .withPath(warehouse.resolve(name + ".parquet").toAbsolutePath().toString())
        .withFormat(FileFormat.PARQUET)
        .withFileSizeInBytes(10)
        .withRecordCount(1)
        .build();
  }

  private List<String> journalRows() {
    return query("SELECT tables || '|' || stamps FROM lake_commit_journal ORDER BY id");
  }

  private List<String> pendingDeletePaths() {
    return query("SELECT path FROM lake_pending_deletes ORDER BY path");
  }

  private List<String> query(final String sql) {
    try (final Connection connection = DriverManager.getConnection(jdbcUrl);
        final Statement statement = connection.createStatement();
        final ResultSet rows = statement.executeQuery(sql)) {
      final List<String> result = new ArrayList<>();
      while (rows.next()) {
        result.add(rows.getString(1));
      }
      return result;
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String stripTrailingSlash(final String uri) {
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.stream.ExportersState;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The exporter state in the database outlives the broker that wrote it, so a broker that takes the
 * partition over continues each exporter where its predecessor stopped.
 */
final class MyBatisExporterStateStoreTest {

  private static final AtomicInteger PARTITION_IDS = new AtomicInteger();
  private static SqlSessionFactory sessionFactory;

  private int partitionId;

  @BeforeAll
  static void createDatabase() throws Exception {
    final var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:exporter-state-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    try (final var connection = dataSource.getConnection()) {
      final var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      new Liquibase(ExporterStateSchema.CHANGELOG, new ClassLoaderResourceAccessor(), database)
          .update();
    }
    sessionFactory = MyBatisExporterStateStore.createSessionFactory(dataSource);
  }

  @BeforeEach
  void usePartitionOfItsOwn() {
    partitionId = PARTITION_IDS.incrementAndGet();
  }

  @Test
  void shouldContinueAnExporterWhereThePreviousLeaderStopped() {
    // given - the former leader exported up to a position and stored metadata
    final var metadata = new UnsafeBuffer("cursor-42".getBytes(StandardCharsets.UTF_8));
    final var formerLeader = exportersState();
    formerLeader.setExporterState("exporter-a", 42, metadata);
    formerLeader.flush();

    // when - the new leader reads the state with stores of its own
    final var newLeader = exportersState();

    // then
    assertThat(newLeader.getPosition("exporter-a")).isEqualTo(42);
    assertThat(newLeader.getExporterMetadata("exporter-a")).isEqualTo(metadata);
    assertThat(newLeader.getLowestPosition()).isEqualTo(42);
  }

  @Test
  void shouldUpdateThePositionOfAnExporter() {
    // given
    final var state = exportersState();
    state.setPosition("exporter-a", 10);

    state.flush();

    // when
    state.setPosition("exporter-a", 20);
    state.flush();

    // then
    assertThat(exportersState().getPosition("exporter-a")).isEqualTo(20);
  }

  @Test
  void shouldKeepTheMetadataWhenOnlyThePositionChanges() {
    // given
    final var metadata = new UnsafeBuffer("cursor".getBytes(StandardCharsets.UTF_8));
    final var state = exportersState();
    state.setExporterState("exporter-a", 10, metadata);

    state.flush();

    // when
    state.setPosition("exporter-a", 11);
    state.flush();

    // then
    assertThat(exportersState().getExporterMetadata("exporter-a")).isEqualTo(metadata);
  }

  @Test
  void shouldKeepTheMetadataVersionOfAnInitializedExporter() {
    // when
    final var initialized = exportersState();
    initialized.initializeExporterState("exporter-a", 5, null, 3);
    initialized.flush();

    // then
    final var state = exportersState();
    assertThat(state.getPosition("exporter-a")).isEqualTo(5);
    assertThat(state.getMetadataVersion("exporter-a")).isEqualTo(3);
  }

  @Test
  void shouldFindTheLowestAndHighestPositionOfAllExporters() {
    // given
    final var state = exportersState();
    state.setPosition("exporter-a", 30);
    state.setPosition("exporter-b", 10);
    state.setPosition("exporter-c", 20);

    // then
    assertThat(state.getLowestPosition()).isEqualTo(10);
    assertThat(state.getHighestPosition()).isEqualTo(30);
    final Map<String, Long> positions = new HashMap<>();
    state.visitExporterState((id, entry) -> positions.put(id, entry.getPosition()));
    assertThat(positions)
        .containsOnly(
            Map.entry("exporter-a", 30L),
            Map.entry("exporter-b", 10L),
            Map.entry("exporter-c", 20L));
  }

  @Test
  void shouldRemoveTheStateOfAnExporter() {
    // given
    final var state = exportersState();
    state.setPosition("exporter-a", 10);
    state.flush();
    state.setPosition("exporter-a", 11);

    // when
    state.removeExporterState("exporter-a");
    state.flush();

    // then - neither the stored nor the held position remains
    assertThat(state.getPosition("exporter-a")).isEqualTo(ExportersState.VALUE_NOT_FOUND);
    assertThat(state.hasExporters()).isFalse();
    assertThat(exportersState().hasExporters()).isFalse();
  }

  @Test
  void shouldKeepThePartitionsApart() {
    // given
    final var state = exportersState();
    state.setPosition("exporter-a", 10);
    state.flush();

    // when
    final var otherPartition =
        new ExportersState(
            new MyBatisExporterStateStore(sessionFactory, partitionId + 1000),
            transactionContext());

    // then
    assertThat(otherPartition.hasExporters()).isFalse();
    assertThat(otherPartition.getPosition("exporter-a")).isEqualTo(ExportersState.VALUE_NOT_FOUND);
  }

  @Test
  void shouldHoldThePositionsBackUntilTheyAreFlushed() {
    // given
    final var state = exportersState();
    state.setPosition("exporter-a", 10);
    state.flush();

    // when - the exporter acknowledges more records
    state.setPosition("exporter-a", 11);
    state.setPosition("exporter-a", 12);

    // then - the director sees them, but a new leader would continue from the stored position
    assertThat(state.getPosition("exporter-a")).isEqualTo(12);
    assertThat(state.getLowestPosition()).isEqualTo(12);
    assertThat(exportersState().getPosition("exporter-a")).isEqualTo(10);

    // when
    state.flush();

    // then
    assertThat(exportersState().getPosition("exporter-a")).isEqualTo(12);
  }

  @Test
  void shouldWriteTheHeldPositionsOfAllExportersInOneFlush() {
    // given
    final var state = exportersState();
    state.setPosition("exporter-a", 10);
    state.setPosition("exporter-b", 20);
    assertThat(exportersState().hasExporters()).isFalse();

    // when
    state.flush();

    // then
    final var newLeader = exportersState();
    assertThat(newLeader.getPosition("exporter-a")).isEqualTo(10);
    assertThat(newLeader.getPosition("exporter-b")).isEqualTo(20);
  }

  private ExportersState exportersState() {
    return new ExportersState(
        new MyBatisExporterStateStore(sessionFactory, partitionId), transactionContext());
  }

  /** The database store commits each operation itself, so the context only groups the calls. */
  private static TransactionContext transactionContext() {
    return new JdbcTransactionContext();
  }
}

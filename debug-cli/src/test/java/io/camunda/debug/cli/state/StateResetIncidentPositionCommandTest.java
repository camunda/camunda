/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class StateResetIncidentPositionCommandTest {

  private static final String EXPORTER_ID = "camundaexporter";
  private static final long EXPORTER_POSITION = 210_000_000L;
  private static final long CORRUPTED_INCIDENT_POSITION = 214_000_000L;
  private static final long FIRST_USER_TASK_KEY = 123L;

  CommandLine commandLine;
  @TempDir Path tempDir;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  public void setup() {
    // Root the command at StateCommand directly so picocli does not eagerly load the sibling
    // TopologyMetaCommand (and its protobuf dependencies) that live under the top-level Main
    // command.
    commandLine = new CommandLine(new StateCommand());
  }

  @Test
  void shouldResetIncidentPositionToDefault() throws Exception {
    // given
    final Path partitionRoot = tempDir.resolve("partitionRoot");
    final var initialSnapshot = takeInitialSnapshot(partitionRoot);

    // when - no --position, so it defaults to -1
    final int exitCode =
        commandLine.execute(
            "reset-incident-position",
            "-v",
            "-r",
            partitionRoot.toString(),
            "--exporter-id=" + EXPORTER_ID,
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then
    assertThat(exitCode).isZero();

    final var state = readState(partitionRoot, initialSnapshot);
    final var metadata = metadataOf(state);
    assertThat(
            metadata.get(StateResetIncidentPositionCommand.LAST_INCIDENT_UPDATE_POSITION).asLong())
        .isEqualTo(-1L);
    assertThat(state.position()).isEqualTo(EXPORTER_POSITION);
    // unrelated metadata is preserved
    assertThat(metadata.get("firstUserTaskKeys").get("ZEEBE_USER_TASK").asLong())
        .isEqualTo(FIRST_USER_TASK_KEY);
  }

  @Test
  void shouldResetIncidentPositionToExplicitValue() throws Exception {
    // given
    final Path partitionRoot = tempDir.resolve("partitionRoot");
    final var initialSnapshot = takeInitialSnapshot(partitionRoot);
    final long target = 85_000_000L;

    // when
    final int exitCode =
        commandLine.execute(
            "reset-incident-position",
            "-r",
            partitionRoot.toString(),
            "--exporter-id=" + EXPORTER_ID,
            "--position=" + target,
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then
    assertThat(exitCode).isZero();

    final var state = readState(partitionRoot, initialSnapshot);
    final var metadata = metadataOf(state);
    assertThat(
            metadata.get(StateResetIncidentPositionCommand.LAST_INCIDENT_UPDATE_POSITION).asLong())
        .isEqualTo(target);
    assertThat(state.position()).isEqualTo(EXPORTER_POSITION);
  }

  @Test
  void shouldFailWhenExporterMissing() {
    // given
    final Path partitionRoot = tempDir.resolve("partitionRoot");
    final var initialSnapshot = takeInitialSnapshot(partitionRoot);

    // when
    final int exitCode =
        commandLine.execute(
            "reset-incident-position",
            "-r",
            partitionRoot.toString(),
            "--exporter-id=unknownExporter",
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then
    assertThat(exitCode).isOne();
    assertThat(listSnapshots(partitionRoot)).containsExactly(initialSnapshot.getId().toString());
  }

  private PersistedSnapshot takeInitialSnapshot(final Path partitionRoot) {
    try (final var initialRuntime =
        newDbFactory().createDb(tempDir.resolve("initialRuntime").toFile())) {
      final var context = initialRuntime.createContext();
      final DbString exporterKey = new DbString();
      final ColumnFamily<DbString, ExporterStateEntry> exporterColumnFamily =
          initialRuntime.createColumnFamily(
              ZbColumnFamilies.EXPORTER, context, exporterKey, new ExporterStateEntry());

      context.runInTransaction(
          () -> {
            final var entry = new ExporterStateEntry();
            entry.setPosition(EXPORTER_POSITION);
            entry.setMetadata(new UnsafeBuffer(initialMetadataJson()));
            exporterKey.wrapString(EXPORTER_ID);
            exporterColumnFamily.upsert(exporterKey, entry);
          });

      return new SnapshotUtil().takeSnapshot(initialRuntime, partitionRoot, "1-1-1-1-1", 1L);
    }
  }

  private byte[] initialMetadataJson() {
    final ObjectNode metadata = objectMapper.createObjectNode();
    metadata.put(
        StateResetIncidentPositionCommand.LAST_INCIDENT_UPDATE_POSITION,
        CORRUPTED_INCIDENT_POSITION);
    final ObjectNode firstUserTaskKeys = metadata.putObject("firstUserTaskKeys");
    firstUserTaskKeys.put("ZEEBE_USER_TASK", FIRST_USER_TASK_KEY);
    firstUserTaskKeys.put("JOB_WORKER", -1L);
    try {
      return objectMapper.writeValueAsBytes(metadata);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Opens the patched snapshot once and extracts the persisted exporter position and metadata bytes
   * inside the read transaction (the entry's buffers are only valid while the transaction is open).
   */
  private ExporterSnapshotState readState(
      final Path partitionRoot, final PersistedSnapshot initialSnapshot) throws Exception {
    final var newSnapshotPath = newSnapshotPath(partitionRoot, initialSnapshot);
    try (final ZeebeDb db =
        new SnapshotUtil().openSnapshot(newSnapshotPath, tempDir.resolve("openedRuntime"))) {
      final var context = db.createContext();
      final DbString exporterKey = new DbString();
      final ColumnFamily<DbString, ExporterStateEntry> exporterColumnFamily =
          db.createColumnFamily(
              ZbColumnFamilies.EXPORTER, context, exporterKey, new ExporterStateEntry());

      final AtomicReference<ExporterSnapshotState> holder = new AtomicReference<>();
      context.runInTransaction(
          () -> {
            exporterKey.wrapString(EXPORTER_ID);
            final var entry = exporterColumnFamily.get(exporterKey);
            final var buffer = entry.getMetadata();
            final byte[] metadata = new byte[buffer.capacity()];
            buffer.getBytes(0, metadata);
            holder.set(new ExporterSnapshotState(entry.getPosition(), metadata));
          });
      return holder.get();
    }
  }

  private JsonNode metadataOf(final ExporterSnapshotState state) throws IOException {
    return objectMapper.readTree(state.metadata());
  }

  private Path newSnapshotPath(final Path partitionRoot, final PersistedSnapshot initialSnapshot) {
    try {
      return Files.list(partitionRoot.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY))
          .filter(Files::isDirectory)
          .filter(dir -> !dir.getFileName().toString().equals(initialSnapshot.getId()))
          .findFirst()
          .orElseThrow();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private java.util.List<String> listSnapshots(final Path partitionRoot) {
    try (final var stream =
        Files.list(partitionRoot.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY))) {
      return stream.filter(Files::isDirectory).map(dir -> dir.getFileName().toString()).toList();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private ZeebeRocksDbFactory<ZbColumnFamilies> newDbFactory() {
    return new ZeebeRocksDbFactory<>(
        new RocksDbConfiguration().setWalDisabled(false),
        new ConsistencyChecksSettings(true, true),
        new AccessMetricsConfiguration(Kind.NONE, 1),
        SimpleMeterRegistry::new);
  }

  private record ExporterSnapshotState(long position, byte[] metadata) {}
}

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
import io.camunda.zeebe.broker.exporter.stream.ExporterStateEntry;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

class StateResetIncidentPositionCommandTest {

  private static final String EXPORTER_ID = StateResetIncidentPositionCommand.DEFAULT_EXPORTER_ID;
  private static final long EXPORTER_POSITION = 210_000_000L;
  private static final long CORRUPTED_INCIDENT_POSITION = 214_000_000L;
  private static final long FIRST_USER_TASK_KEY = 123L;

  CommandLine commandLine;
  @TempDir Path tempDir;
  StringWriter err;
  StringWriter out;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  public void setup() {
    // Root the command at StateCommand directly so picocli does not eagerly load the sibling
    // TopologyMetaCommand (and its protobuf dependencies) that live under the top-level Main
    // command.
    err = new StringWriter();
    out = new StringWriter();
    commandLine =
        new CommandLine(new StateCommand())
            .setErr(new PrintWriter(err))
            .setOut(new PrintWriter(out));
  }

  @ParameterizedTest
  @ValueSource(longs = {-1L, 85_000_000L})
  void shouldResetIncidentPosition(final long targetPosition) throws Exception {
    // given
    final Path partitionRoot = tempDir.resolve("partitionRoot");
    final var initialSnapshot = takeInitialSnapshot(partitionRoot);

    // when - no --exporter-id, so it defaults to camundaexporter
    final int exitCode =
        commandLine.execute(
            "reset-incident-position",
            "-v",
            "-r",
            partitionRoot.toString(),
            "--position=" + targetPosition,
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then
    assertThat(exitCode).isZero();

    final var state = readState(partitionRoot, initialSnapshot);
    final var metadata = metadataOf(state);
    assertThat(
            metadata.get(StateResetIncidentPositionCommand.LAST_INCIDENT_UPDATE_POSITION).asLong())
        .isEqualTo(targetPosition);
    assertThat(state.position()).isEqualTo(EXPORTER_POSITION);
    // unrelated metadata is preserved
    assertThat(metadata.get("firstUserTaskKeys").get("ZEEBE_USER_TASK").asLong())
        .isEqualTo(FIRST_USER_TASK_KEY);
    // stdout carries only the machine-readable path of the new snapshot
    final var newSnapshotPath =
        SnapshotTestUtil.newSnapshotPath(partitionRoot, initialSnapshot.getId());
    assertThat(out.toString().trim()).isEqualTo(newSnapshotPath.toString());
    assertThat(err.toString()).doesNotContain("WARNING");
  }

  @Test
  void shouldWarnWhenPositionAboveExporterPosition() throws Exception {
    // given
    final Path partitionRoot = tempDir.resolve("partitionRoot");
    final var initialSnapshot = takeInitialSnapshot(partitionRoot);
    final long target = EXPORTER_POSITION + 1;

    // when
    final int exitCode =
        commandLine.execute(
            "reset-incident-position",
            "-r",
            partitionRoot.toString(),
            "--position=" + target,
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then - the value is written as requested, but a warning is logged
    assertThat(exitCode).isZero();
    assertThat(err.toString()).contains("WARNING").contains("exporterPosition");

    final var state = readState(partitionRoot, initialSnapshot);
    assertThat(
            metadataOf(state)
                .get(StateResetIncidentPositionCommand.LAST_INCIDENT_UPDATE_POSITION)
                .asLong())
        .isEqualTo(target);
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
            "--position=-1",
            "--snapshot=" + initialSnapshot.getId().toString(),
            "--runtime=" + tempDir.resolve("runtime"));

    // then
    assertThat(exitCode).isOne();
    assertThat(SnapshotTestUtil.listSnapshots(partitionRoot))
        .containsExactly(initialSnapshot.getId().toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"root", "position", "snapshot", "runtime"})
  void shouldFailIfRequiredArgumentIsNotSet(final String optionToSkip) {
    // given
    final var options = new ArrayList<>(List.of("reset-incident-position"));
    final var requiredOptions =
        List.of(
            "--root=rootFolder",
            "--position=-1",
            "--snapshot=exampleId",
            "--runtime=" + tempDir.resolve("runtime"));

    options.addAll(
        requiredOptions.stream()
            // skip the option if matches
            .filter(o -> !o.split("=")[0].equals("--" + optionToSkip))
            .toList());

    // when
    final int exitCode = commandLine.execute(options.toArray(String[]::new));

    // then
    assertThat(exitCode).isPositive();
    assertThat(err.toString()).contains("Missing required option: '--%s=".formatted(optionToSkip));
  }

  private PersistedSnapshot takeInitialSnapshot(final Path partitionRoot) {
    try (final var initialRuntime =
        SnapshotTestUtil.newDbFactory().createDb(tempDir.resolve("initialRuntime").toFile())) {
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
    final var newSnapshotPath =
        SnapshotTestUtil.newSnapshotPath(partitionRoot, initialSnapshot.getId());
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

  private record ExporterSnapshotState(long position, byte[] metadata) {}
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreImpl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.agrona.concurrent.UnsafeBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Offline recovery command: lowers an exporter's {@code lastIncidentUpdatePosition} in a stopped
 * broker's snapshot while preserving its {@code exporterPosition}.
 *
 * <p>The cursor lives inside the JSON {@code ExporterMetadata} blob stored in the {@code EXPORTER}
 * column family. We edit that JSON directly with a generic tree model rather than depending on
 * {@code camunda-exporter} (which would drag the Elasticsearch/OpenSearch client stack into this
 * CLI); unknown fields such as {@code firstUserTaskKeys} are preserved untouched. Writing the
 * lowered value here is safe even though the runtime setter has a monotonic guard: when the broker
 * recovers it deserializes into a fresh metadata object whose guard floor is {@code -1}, so the
 * lowered value is accepted.
 */
@Command(
    name = "reset-incident-position",
    description =
        "Reset an exporter's lastIncidentUpdatePosition in an offline snapshot, preserving its exporterPosition")
public class StateResetIncidentPositionCommand implements Callable<Integer> {

  static final String LAST_INCIDENT_UPDATE_POSITION = "lastIncidentUpdatePosition";
  private static final long UNSET_POSITION = -1L;
  private static final String DEFAULT_RESET_POSITION = "-1";

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  protected boolean verbose;

  @Spec CommandSpec spec;

  @Option(
      names = {"-r", "--root"},
      description =
          "Path of the partition directory (the folder containing 'snapshots/'), e.g. "
              + "<data>/raft-partition/partitions/<id>",
      required = true)
  private Path root;

  @ParentCommand private StateCommand parentCommand;

  @Option(
      names = {"--runtime"},
      description = "Path to the temporary runtime directory",
      required = true)
  private Path runtimePath;

  @Option(
      names = {"-s", "--snapshot"},
      description = "Id of the source snapshot directory",
      required = true)
  private String snapshotId;

  @Option(
      names = {"-e", "--exporter-id"},
      description = "Id of the exporter whose cursor should be reset",
      required = true)
  private String exporterId;

  @Option(
      names = {"--position"},
      description =
          "New lastIncidentUpdatePosition value. Defaults to -1 (reprocess all incidents from the "
              + "start).",
      defaultValue = DEFAULT_RESET_POSITION)
  private long newPosition;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private String validationError;

  @Override
  public Integer call() throws Exception {
    final var out = spec.commandLine().getOut();
    final var err = spec.commandLine().getErr();

    out.println("=== Starting exporter incident-position reset ===");
    validationError = null;

    if (verbose) {
      err.println("Exporter ID: " + exporterId);
      err.println("New lastIncidentUpdatePosition: " + newPosition);
      err.println("Root path: " + root);
      err.println("Snapshot ID: " + snapshotId);
      err.println("Runtime path: " + runtimePath);
    }

    final var snapshotUtil = new SnapshotUtil();
    final var snapshotPath =
        root.resolve(FileBasedSnapshotStoreImpl.SNAPSHOTS_DIRECTORY).resolve(snapshotId);

    if (verbose) {
      err.println("\nOpening snapshot from: " + snapshotPath);
    }
    // try-with-resources so the RocksDB instance (and its file locks on --runtime) is always
    // released, including on the validation-error return path.
    try (final ZeebeDb db = snapshotUtil.openSnapshot(snapshotPath, runtimePath)) {
      final var context = db.createContext();

      final DbString exporterKey = new DbString();
      final ColumnFamily<DbString, ExporterStateEntry> exporterColumnFamily =
          db.createColumnFamily(
              ZbColumnFamilies.EXPORTER, context, exporterKey, new ExporterStateEntry());

      if (verbose) {
        err.println("\nExecuting exporter metadata update transaction...");
      }
      context.runInTransaction(() -> resetIncidentPosition(exporterColumnFamily, exporterKey));

      if (validationError != null) {
        err.println("Error: " + validationError);
        return 1;
      }

      final var lastFollowupEventPosition = SnapshotUtil.getLastFollowupEventPosition(snapshotPath);
      if (verbose) {
        err.println("\nTaking new snapshot...");
      }
      final var persistedSnapshot =
          snapshotUtil.takeSnapshot(db, root, snapshotId, lastFollowupEventPosition);

      out.println("\n=== Exporter incident-position reset completed successfully ===");
      out.println("Created new snapshot at: " + persistedSnapshot.getPath());
      out.println("\nNext steps:");
      out.println("1. Delete the previous snapshot: " + snapshotId);
      out.println("2. Restart this broker so it recovers from the patched snapshot");
      out.println(
          "3. Repeat on every replica of this partition: run against each broker's own latest "
              + "snapshot.");

      return 0;
    }
  }

  private void resetIncidentPosition(
      final ColumnFamily<DbString, ExporterStateEntry> exporterColumnFamily,
      final DbString exporterKey) {
    final var out = spec.commandLine().getOut();

    exporterKey.wrapString(exporterId);
    final var entry = exporterColumnFamily.get(exporterKey);
    if (entry == null) {
      validationError =
          "No state found for exporter '"
              + exporterId
              + "' in the EXPORTER column family of this snapshot";
      return;
    }

    final long exporterPosition = entry.getPosition();

    final ObjectNode metadataNode;
    try {
      metadataNode = readMetadata(entry);
    } catch (final IOException e) {
      validationError = "Failed to parse exporter metadata JSON: " + e.getMessage();
      return;
    }

    final long currentIncidentPosition =
        metadataNode.has(LAST_INCIDENT_UPDATE_POSITION)
            ? metadataNode.get(LAST_INCIDENT_UPDATE_POSITION).asLong()
            : UNSET_POSITION;

    out.println("  Exporter exporterPosition (preserved): " + exporterPosition);
    out.println("  Current lastIncidentUpdatePosition: " + currentIncidentPosition);
    out.println("  New lastIncidentUpdatePosition: " + newPosition);

    metadataNode.put(LAST_INCIDENT_UPDATE_POSITION, newPosition);

    final byte[] patched;
    try {
      patched = objectMapper.writeValueAsBytes(metadataNode);
    } catch (final JsonProcessingException e) {
      validationError = "Failed to serialize patched exporter metadata: " + e.getMessage();
      return;
    }

    entry.setMetadata(new UnsafeBuffer(patched));
    exporterKey.wrapString(exporterId);
    exporterColumnFamily.upsert(exporterKey, entry);
    out.println("  ✓ lastIncidentUpdatePosition updated successfully");
  }

  private ObjectNode readMetadata(final ExporterStateEntry entry) throws IOException {
    final var metadata = entry.getMetadata();
    final int length = metadata.capacity();
    if (length == 0) {
      // No metadata persisted yet: start from an empty object so we only add the cursor field.
      return objectMapper.createObjectNode();
    }
    final byte[] bytes = new byte[length];
    metadata.getBytes(0, bytes);
    final var node = objectMapper.readTree(bytes);
    if (node instanceof final ObjectNode objectNode) {
      return objectNode;
    }
    throw new IOException("exporter metadata is not a JSON object");
  }
}

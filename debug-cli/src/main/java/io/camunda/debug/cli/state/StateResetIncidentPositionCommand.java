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
import io.camunda.zeebe.broker.exporter.stream.ExporterStateEntry;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;
import org.agrona.concurrent.UnsafeBuffer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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
public class StateResetIncidentPositionCommand extends SnapshotEditCommand {

  static final String LAST_INCIDENT_UPDATE_POSITION = "lastIncidentUpdatePosition";
  static final String DEFAULT_EXPORTER_ID = "camundaexporter";
  private static final long UNSET_POSITION = -1L;

  @Option(
      names = {"-e", "--exporter-id"},
      description =
          "Id of the exporter whose cursor should be reset, i.e. the key the exporter is "
              + "configured under in zeebe.broker.exporters (default: ${DEFAULT-VALUE})",
      defaultValue = DEFAULT_EXPORTER_ID)
  private String exporterId;

  @Option(
      names = {"--position"},
      description =
          "New lastIncidentUpdatePosition value. Use -1 to reprocess all incidents from the start.",
      required = true)
  private long newPosition;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  String operationName() {
    return "exporter incident-position reset";
  }

  @Override
  String runbookReference() {
    return "debug-cli/scripts/reset-incident-position/README.md";
  }

  @Override
  void printVerboseOptions(final PrintWriter err) {
    err.println("Exporter ID: " + exporterId);
    err.println("New lastIncidentUpdatePosition: " + newPosition);
  }

  @Override
  String edit(final ZeebeDb db, final TransactionContext context) {
    final DbString exporterKey = new DbString();
    final ColumnFamily<DbString, ExporterStateEntry> exporterColumnFamily =
        db.createColumnFamily(
            ZbColumnFamilies.EXPORTER, context, exporterKey, new ExporterStateEntry());

    final var error = new AtomicReference<String>();
    context.runInTransaction(
        () -> error.set(resetIncidentPosition(exporterColumnFamily, exporterKey)));
    return error.get();
  }

  /** Applies the edit and returns {@code null} on success or a validation-error message. */
  private String resetIncidentPosition(
      final ColumnFamily<DbString, ExporterStateEntry> exporterColumnFamily,
      final DbString exporterKey) {
    final var err = err();

    exporterKey.wrapString(exporterId);
    final var entry = exporterColumnFamily.get(exporterKey);
    if (entry == null) {
      return "No state found for exporter '"
          + exporterId
          + "' in the EXPORTER column family of this snapshot";
    }

    final long exporterPosition = entry.getPosition();

    final ObjectNode metadataNode;
    try {
      metadataNode = readMetadata(entry);
    } catch (final IOException e) {
      return "Failed to parse exporter metadata JSON: " + e.getMessage();
    }

    final long currentIncidentPosition =
        metadataNode.has(LAST_INCIDENT_UPDATE_POSITION)
            ? metadataNode.get(LAST_INCIDENT_UPDATE_POSITION).asLong()
            : UNSET_POSITION;

    err.println("  Exporter exporterPosition (preserved): " + exporterPosition);
    err.println("  Current lastIncidentUpdatePosition: " + currentIncidentPosition);
    err.println("  New lastIncidentUpdatePosition: " + newPosition);

    if (newPosition > exporterPosition) {
      err.println(
          "WARNING: the new lastIncidentUpdatePosition ("
              + newPosition
              + ") is above the exporter's exporterPosition ("
              + exporterPosition
              + "). This leaves the incident-update cursor ahead of the exported log position, so "
              + "incident updates below the new value are silently skipped - the exact situation "
              + "this command is meant to repair.");
    }

    metadataNode.put(LAST_INCIDENT_UPDATE_POSITION, newPosition);

    final byte[] patched;
    try {
      patched = objectMapper.writeValueAsBytes(metadataNode);
    } catch (final JsonProcessingException e) {
      return "Failed to serialize patched exporter metadata: " + e.getMessage();
    }

    entry.setMetadata(new UnsafeBuffer(patched));
    exporterKey.wrapString(exporterId);
    exporterColumnFamily.upsert(exporterKey, entry);
    err.println("  ✓ lastIncidentUpdatePosition updated successfully");
    return null;
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

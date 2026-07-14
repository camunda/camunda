/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.search.clients.DocumentBasedSchemaClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The document serving {@link DatasetSchemaManager}: derives an index mapping from the compiled
 * dataset and provisions it through the neutral {@link DocumentBasedSchemaClient}. A cube index
 * holds one document per cell (the grain dimensions + window/tier + every meter's serving fields —
 * ADR 0009); a snapshot-declaring cube also gets its {@code _snapshots} index (same grain and meter
 * fields keyed by {@code sample_time} — ADR 0010); a table index holds one document per row.
 */
public final class DocumentDatasetSchemaManager implements DatasetSchemaManager {

  private final DocumentBasedSchemaClient schemaClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DocumentDatasetSchemaManager(final DocumentBasedSchemaClient schemaClient) {
    this.schemaClient = schemaClient;
  }

  @Override
  public void ensure(final CompiledDataset dataset) {
    final Map<String, Object> properties = new LinkedHashMap<>();
    putGrainProperties(properties, dataset);
    properties.put(DocumentCubeNames.WINDOW_START, property("long"));
    properties.put(DocumentCubeNames.WINDOW_SIZE, property("long"));
    putMeterProperties(properties, dataset);
    putEnvelopeProperties(properties);
    schemaClient.createIndex(DocumentCubeNames.datasetIndex(dataset.cubeId()), mapping(properties));

    if (dataset.hasSnapshots()) {
      final Map<String, Object> snapshotProperties = new LinkedHashMap<>();
      putGrainProperties(snapshotProperties, dataset);
      snapshotProperties.put(DocumentCubeNames.SAMPLE_TIME, property("long"));
      putMeterProperties(snapshotProperties, dataset);
      putEnvelopeProperties(snapshotProperties);
      schemaClient.createIndex(
          DocumentCubeNames.snapshotIndex(dataset.cubeId()), mapping(snapshotProperties));
    }
  }

  @Override
  public void ensureTable(final CompiledTable table) {
    final Map<String, Object> properties = new LinkedHashMap<>();
    for (final DimensionColumn column : table.columns()) {
      properties.put(DocumentCubeNames.field(column.name()), property(fieldType(column.type())));
    }
    putEnvelopeProperties(properties);
    schemaClient.createIndex(DocumentCubeNames.rowIndex(table.cubeId()), mapping(properties));
  }

  private static void putGrainProperties(
      final Map<String, Object> properties, final CompiledDataset dataset) {
    for (final DimensionColumn column : dataset.grain().columns()) {
      properties.put(DocumentCubeNames.field(column.name()), property(fieldType(column.type())));
    }
  }

  /**
   * Every meter's serving fields, namespaced per meter (mirroring the RDBMS columns): an additive
   * meter maps its pushdown columns onto native numeric fields the composite aggregation reduces; a
   * sketch/summary keeps its blob plus a finalized value field for the DIRECT fast path.
   */
  private static void putMeterProperties(
      final Map<String, Object> properties, final CompiledDataset dataset) {
    for (final CompiledMeter meter : dataset.meters()) {
      final Optional<PushdownSpec<?, ?>> spec = meter.pushdown();
      if (spec.isPresent()) {
        for (final PushdownColumn column : spec.get().columns()) {
          properties.put(
              DocumentCubeNames.pushdownField(meter.meterName(), column.suffix()),
              property(fieldType(column.type())));
        }
      } else {
        properties.put(DocumentCubeNames.blobField(meter.meterName()), property("binary"));
        properties.put(DocumentCubeNames.valueField(meter.meterName()), property("double"));
      }
    }
  }

  /** The shared document envelope: the sortable doc key and the diagnostic version fields. */
  private static void putEnvelopeProperties(final Map<String, Object> properties) {
    properties.put(DocumentCubeNames.DOC_KEY, property("keyword"));
    properties.put(DocumentCubeNames.VER_EPOCH, property("long"));
    properties.put(DocumentCubeNames.VER_OFFSET, property("long"));
  }

  private String mapping(final Map<String, Object> properties) {
    try {
      return objectMapper.writeValueAsString(Map.of("properties", properties));
    } catch (final Exception e) {
      throw new IllegalStateException("failed to build index mapping", e);
    }
  }

  private static Map<String, Object> property(final String type) {
    return Map.of("type", type);
  }

  private static String fieldType(final DimensionType type) {
    return switch (type) {
      case STRING -> "keyword";
      case TEXT -> "text"; // large payload (e.g. BPMN XML): analyzed text, not a keyword
      case LONG -> "long";
      case INT -> "integer";
      case BOOLEAN -> "boolean";
    };
  }
}

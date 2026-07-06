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
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.search.clients.DocumentBasedSchemaClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The document serving {@link DatasetSchemaManager}: derives an index mapping from the compiled
 * dataset and provisions it through the neutral {@link DocumentBasedSchemaClient}. A cube index
 * holds one document per meter-cell (the grain dimensions + window/tier + {@code meter_name} + the
 * {@code accumulator} Base64 blob); a table index holds one document per row (its columns).
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
    for (final DimensionColumn column : dataset.grain().columns()) {
      properties.put(DocumentCubeNames.field(column.name()), property(fieldType(column.type())));
    }
    properties.put(DocumentCubeNames.WINDOW_START, property("long"));
    properties.put(DocumentCubeNames.WINDOW_SIZE, property("long"));
    properties.put(DocumentCubeNames.METER_NAME, property("keyword"));
    properties.put(DocumentCubeNames.ACCUMULATOR, property("binary"));
    schemaClient.createIndex(DocumentCubeNames.datasetIndex(dataset.cubeId()), mapping(properties));
  }

  @Override
  public void ensureTable(final CompiledTable table) {
    final Map<String, Object> properties = new LinkedHashMap<>();
    for (final DimensionColumn column : table.columns()) {
      properties.put(DocumentCubeNames.field(column.name()), property(fieldType(column.type())));
    }
    schemaClient.createIndex(DocumentCubeNames.rowIndex(table.cubeId()), mapping(properties));
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
      case LONG -> "long";
      case INT -> "integer";
      case BOOLEAN -> "boolean";
    };
  }
}

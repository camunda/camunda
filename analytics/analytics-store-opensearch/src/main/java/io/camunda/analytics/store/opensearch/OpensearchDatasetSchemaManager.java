/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.opensearch;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledProjection;
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionType;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;

/**
 * The OpenSearch {@link DatasetSchemaManager}: provisions a cube's {@code dataset_<id>} index or a
 * projected dataset's {@code projection_<id>} index from its compiled schema (MANAGED mode).
 * Dimensions map to {@code keyword}/{@code long}/{@code integer}/{@code boolean}, the implicit
 * {@code window_start}/{@code window_size} to {@code long}, and each meter accumulator to a {@code
 * binary} field (a Base64 blob). Idempotent: an already-existing index is left as is.
 */
public final class OpensearchDatasetSchemaManager implements DatasetSchemaManager {

  private final OpenSearchClient client;

  public OpensearchDatasetSchemaManager(final OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public void ensure(final CompiledDataset dataset) {
    final Map<String, Property> properties = new LinkedHashMap<>();
    for (final DimensionColumn column : dataset.grain().columns()) {
      properties.put(OsNames.field(column.name()), dimensionProperty(column.type()));
    }
    properties.put("window_start", longProperty());
    properties.put("window_size", longProperty());
    for (final String meter : dataset.schema().meterNames()) {
      properties.put(OsNames.field(meter), binaryProperty());
    }
    createIndex(OsNames.datasetIndex(dataset.cubeId()), properties, "cube " + dataset.name());
  }

  @Override
  public void ensureProjection(final CompiledProjection projection) {
    final Map<String, Property> properties = new LinkedHashMap<>();
    for (final DimensionColumn column : projection.columns()) {
      properties.put(OsNames.field(column.name()), dimensionProperty(column.type()));
    }
    createIndex(
        OsNames.projectionIndex(projection.cubeId()),
        properties,
        "projection " + projection.name());
  }

  private void createIndex(
      final String index, final Map<String, Property> properties, final String what) {
    try {
      if (client.indices().exists(e -> e.index(index)).value()) {
        return;
      }
      client.indices().create(c -> c.index(index).mappings(m -> m.properties(properties)));
    } catch (final IOException e) {
      throw new IllegalStateException("failed to provision " + what, e);
    }
  }

  private static Property dimensionProperty(final DimensionType type) {
    return switch (type) {
      case STRING -> Property.of(p -> p.keyword(k -> k));
      case LONG -> longProperty();
      case INT -> Property.of(p -> p.integer(i -> i));
      case BOOLEAN -> Property.of(p -> p.boolean_(b -> b));
    };
  }

  private static Property longProperty() {
    return Property.of(p -> p.long_(l -> l));
  }

  private static Property binaryProperty() {
    return Property.of(p -> p.binary(b -> b));
  }
}

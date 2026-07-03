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
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * The OpenSearch {@link DatasetWriter}: idempotent upserts keyed by a deterministic document id. A
 * cube cell is a partial {@code doc_as_upsert} update that sets the grain fields, window/tier, and
 * only the one meter's Base64 blob, so meters of the same cell coexist; a projected row is a full
 * document index by its row key. Re-writing the same id overwrites rather than duplicates.
 */
public final class OpensearchDatasetWriter implements DatasetWriter {

  private final OpenSearchClient client;

  public OpensearchDatasetWriter(final OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public void upsertCell(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final String meterName,
      final byte[] accumulator) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> grain = dataset.grain().columns();
    for (int i = 0; i < grain.size(); i++) {
      doc.put(OsNames.field(grain.get(i).name()), key.get(i));
    }
    doc.put("window_start", windowStart);
    doc.put("window_size", windowSize);
    doc.put(OsNames.field(meterName), OsNames.encode(accumulator));
    final String index = OsNames.datasetIndex(dataset.cubeId());
    final String id = OsNames.cellId(key, windowStart, windowSize);
    try {
      client.update(u -> u.index(index).id(id).docAsUpsert(true).doc(doc), Map.class);
    } catch (final IOException e) {
      throw new IllegalStateException("failed to upsert cell into " + dataset.name(), e);
    }
  }

  @Override
  public void upsertRow(
      final CompiledProjection projection, final String rowKey, final List<Object> values) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> columns = projection.columns();
    for (int i = 0; i < columns.size(); i++) {
      doc.put(OsNames.field(columns.get(i).name()), values.get(i));
    }
    final String index = OsNames.projectionIndex(projection.cubeId());
    try {
      client.index(i -> i.index(index).id(rowKey).document(doc));
    } catch (final IOException e) {
      throw new IllegalStateException("failed to upsert row into " + projection.name(), e);
    }
  }

  @Override
  public void flush() {
    // writes are applied per call; a bulk/refresh batching pass is a later optimization
  }

  @Override
  public void close() {
    // the client is owned by the store
  }
}

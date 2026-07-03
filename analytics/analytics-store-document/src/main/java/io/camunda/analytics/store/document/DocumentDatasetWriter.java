/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledProjection;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.RequestBuilders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The document serving {@link DatasetWriter}: each write is a full {@code index()} of the current
 * value with a deterministic id, which is an idempotent overwrite (the serving store holds current
 * values, not deltas). A cube cell writes one document per meter, keyed by {@code (dims, window,
 * tier, meter)}; a projected row writes one document keyed by its row key. Meters of the same cell
 * are independent documents, so setting one never clobbers another.
 */
public final class DocumentDatasetWriter implements DatasetWriter {

  private final DocumentBasedWriteClient writeClient;

  public DocumentDatasetWriter(final DocumentBasedWriteClient writeClient) {
    this.writeClient = writeClient;
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
      doc.put(DocumentCubeNames.field(grain.get(i).name()), key.get(i));
    }
    doc.put(DocumentCubeNames.WINDOW_START, windowStart);
    doc.put(DocumentCubeNames.WINDOW_SIZE, windowSize);
    doc.put(DocumentCubeNames.METER_NAME, meterName);
    doc.put(DocumentCubeNames.ACCUMULATOR, DocumentCubeNames.encode(accumulator));
    final String id = DocumentCubeNames.cellDocId(key, windowStart, windowSize, meterName);
    writeClient.index(
        RequestBuilders.<Map<String, Object>>indexRequest(
            r -> r.index(DocumentCubeNames.datasetIndex(dataset.cubeId())).id(id).document(doc)));
  }

  @Override
  public void upsertRow(
      final CompiledProjection projection, final String rowKey, final List<Object> values) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> columns = projection.columns();
    for (int i = 0; i < columns.size(); i++) {
      doc.put(DocumentCubeNames.field(columns.get(i).name()), values.get(i));
    }
    writeClient.index(
        RequestBuilders.<Map<String, Object>>indexRequest(
            r ->
                r.index(DocumentCubeNames.projectionIndex(projection.cubeId()))
                    .id(rowKey)
                    .document(doc)));
  }

  @Override
  public void flush() {
    // each index() is applied on call; a bulk/refresh batching pass is a later optimization
  }

  @Override
  public void close() {
    // the client is owned by the store
  }
}

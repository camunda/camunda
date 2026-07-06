/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.analytics.dataset.store.SketchScalar;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.RequestBuilders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    // An additive meter writes its native numeric fields only (the composite aggregation reduces
    // them). A sketch/summary writes its app-mergeable blob (streamed + merged) plus a finalized
    // scalar for the DIRECT fast path — no blob for additive, which is never streamed.
    final CompiledMeter compiled = compiledMeter(dataset, meterName, windowSize);
    final Optional<PushdownSpec<?, ?>> spec = compiled.pushdown();
    if (spec.isPresent()) {
      final List<Object> values = decompose(compiled.bound(), accumulator);
      final List<PushdownColumn> columns = spec.get().columns();
      for (int i = 0; i < columns.size(); i++) {
        doc.put(DocumentCubeNames.pushdownField(columns.get(i).suffix()), values.get(i));
      }
    } else {
      doc.put(DocumentCubeNames.ACCUMULATOR, DocumentCubeNames.encode(accumulator));
      doc.put(DocumentCubeNames.VALUE, SketchScalar.of(finalized(compiled.bound(), accumulator)));
    }

    final String id = DocumentCubeNames.cellDocId(key, windowStart, windowSize, meterName);
    doc.put(DocumentCubeNames.DOC_KEY, id); // sortable copy of the id, for search_after streaming
    writeClient.index(
        RequestBuilders.<Map<String, Object>>indexRequest(
            r -> r.index(DocumentCubeNames.datasetIndex(dataset.cubeId())).id(id).document(doc)));
  }

  private static CompiledMeter compiledMeter(
      final CompiledDataset dataset, final String meterName, final long windowSize) {
    CompiledMeter fallback = null;
    for (final CompiledMeter meter : dataset.meters()) {
      if (meter.meterName().equals(meterName)) {
        if (meter.windowMs() == windowSize) {
          return meter;
        }
        fallback = meter;
      }
    }
    if (fallback == null) {
      throw new IllegalStateException(
          "no compiled meter '" + meterName + "' in cube '" + dataset.name() + "'");
    }
    return fallback;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> decompose(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final Object accumulator = bound.accumulatorCodec().fromBytes(bytes);
    return bound.pushdown().orElseThrow().decompose().apply(accumulator);
  }

  @SuppressWarnings("unchecked")
  private static Object finalized(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    return bound.aggregate().getResult(bound.accumulatorCodec().fromBytes(bytes));
  }

  @Override
  public void upsertRow(final CompiledTable table, final String rowKey, final List<Object> values) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> columns = table.columns();
    for (int i = 0; i < columns.size(); i++) {
      doc.put(DocumentCubeNames.field(columns.get(i).name()), values.get(i));
    }
    writeClient.index(
        RequestBuilders.<Map<String, Object>>indexRequest(
            r -> r.index(DocumentCubeNames.rowIndex(table.cubeId())).id(rowKey).document(doc)));
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

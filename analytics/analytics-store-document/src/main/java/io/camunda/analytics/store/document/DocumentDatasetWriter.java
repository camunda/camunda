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
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.PushdownColumn;
import io.camunda.analytics.meter.PushdownSpec;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import io.camunda.analytics.serving.spi.WriteVersion;
import io.camunda.analytics.serving.support.SketchScalar;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.RequestBuilders;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The document serving {@link DatasetWriter}: each write is a full {@code index()} of the current
 * value with a deterministic id, which is an idempotent overwrite (the serving store holds current
 * values, not deltas). A cube cell arrives as one composite accumulator (ADR 0009) and fans out
 * into one document per meter, keyed by {@code (dims, window, tier, meter)}; a projected row writes
 * one document keyed by its row key. Meters of the same cell are independent documents, so setting
 * one never clobbers another.
 */
public final class DocumentDatasetWriter implements VersionedDatasetWriter {

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
      final byte[] compositeAccumulator,
      final WriteVersion version) {
    // The composite carries every meter's slot (ADR 0009). The document layout stays one document
    // per meter for now — the writer fans the composite out — so the read path (which regroups
    // meter documents into cells) is untouched; collapsing to one document per cell (and a native
    // external-version write fence) is the documented follow-up.
    final List<CompiledMeter> meters = dataset.meters();
    final List<byte[]> slots =
        CompositeAccumulatorValue.slotBytes(compositeAccumulator, meters.size());
    for (int slot = 0; slot < meters.size(); slot++) {
      upsertMeterDocument(
          dataset, key, windowStart, windowSize, meters.get(slot), slots.get(slot), version);
    }
  }

  private void upsertMeterDocument(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final CompiledMeter meter,
      final byte[] slotBytes,
      final WriteVersion version) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> grain = dataset.grain().columns();
    for (int i = 0; i < grain.size(); i++) {
      doc.put(DocumentCubeNames.field(grain.get(i).name()), key.get(i));
    }
    doc.put(DocumentCubeNames.WINDOW_START, windowStart);
    doc.put(DocumentCubeNames.WINDOW_SIZE, windowSize);
    doc.put(DocumentCubeNames.METER_NAME, meter.meterName());

    // An additive meter writes its native numeric fields only (the composite aggregation reduces
    // them). A sketch/summary writes its app-mergeable blob (streamed + merged) plus a finalized
    // scalar for the DIRECT fast path — no blob for additive, which is never streamed.
    final Optional<PushdownSpec<?, ?>> spec = meter.pushdown();
    if (spec.isPresent()) {
      final List<Object> values = decompose(meter.bound(), slotBytes);
      final List<PushdownColumn> columns = spec.get().columns();
      for (int i = 0; i < columns.size(); i++) {
        doc.put(DocumentCubeNames.pushdownField(columns.get(i).suffix()), values.get(i));
      }
    } else {
      final byte[] blob = slotBytes == null ? emptySlot(meter.bound()) : slotBytes;
      doc.put(DocumentCubeNames.ACCUMULATOR, DocumentCubeNames.encode(blob));
      doc.put(DocumentCubeNames.VALUE, SketchScalar.of(finalized(meter.bound(), blob)));
    }

    final String id = DocumentCubeNames.cellDocId(key, windowStart, windowSize, meter.meterName());
    doc.put(DocumentCubeNames.DOC_KEY, id); // sortable copy of the id, for search_after streaming
    // The write fence is carried as fields for now: the shared search-client index request has no
    // external-version support yet, so a stale write is not REJECTED here — enforcement needs
    // either that API (version_type=external_gte with the pair packed into ES's single long) or
    // the one-document-per-cell layout follow-up. Recording the version keeps documents
    // diagnosable and the layout forward-compatible in the meantime.
    doc.put(DocumentCubeNames.VER_EPOCH, version.epoch());
    doc.put(DocumentCubeNames.VER_OFFSET, version.offset());
    writeClient.index(
        RequestBuilders.<Map<String, Object>>indexRequest(
            r -> r.index(DocumentCubeNames.datasetIndex(dataset.cubeId())).id(id).document(doc)));
  }

  @SuppressWarnings("unchecked")
  private static List<Object> decompose(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    final Object accumulator =
        bytes == null
            ? bound.aggregate().createAccumulator()
            : bound.accumulatorCodec().fromBytes(bytes);
    return bound.pushdown().orElseThrow().decompose().apply(accumulator);
  }

  /** The encoded empty accumulator — an absent slot's blob value. */
  @SuppressWarnings("unchecked")
  private static byte[] emptySlot(final BoundMeter<?, ?> boundRaw) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    return bound.accumulatorCodec().toBytes(bound.aggregate().createAccumulator());
  }

  @SuppressWarnings("unchecked")
  private static Object finalized(final BoundMeter<?, ?> boundRaw, final byte[] bytes) {
    final BoundMeter<Object, Object> bound = (BoundMeter<Object, Object>) boundRaw;
    return bound.aggregate().getResult(bound.accumulatorCodec().fromBytes(bytes));
  }

  @Override
  public void upsertRow(
      final CompiledTable table,
      final String rowKey,
      final List<Object> values,
      final WriteVersion version) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> columns = table.columns();
    for (int i = 0; i < columns.size(); i++) {
      doc.put(DocumentCubeNames.field(columns.get(i).name()), values.get(i));
    }
    doc.put(DocumentCubeNames.VER_EPOCH, version.epoch());
    doc.put(DocumentCubeNames.VER_OFFSET, version.offset());
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

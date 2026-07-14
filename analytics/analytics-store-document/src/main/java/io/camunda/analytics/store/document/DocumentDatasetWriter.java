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
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchWriteResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

/**
 * The document serving {@link DatasetWriter}: each write is a full {@code index()} of the current
 * value with a deterministic id, which is an idempotent overwrite (the serving store holds current
 * values, not deltas). A cube cell is <b>one document</b> carrying every meter's serving columns
 * from the composite accumulator (ADR 0009 — one writer per row, one atomic upsert, never torn): an
 * additive meter's pushdown columns as native numeric fields, a sketch's app-mergeable blob plus
 * its finalized scalar. Snapshot rows and projected rows are likewise one document each.
 *
 * <p>The write fence is the store's external versioning: every write carries the {@link
 * WriteVersion} packed into one order-preserving {@code long} (see {@link DocumentVersions}) with
 * {@code external_gte} semantics, so the store itself rejects a fenced zombie's stale overwrite —
 * an equal-or-newer version applies (idempotent replay), an older one affects nothing and is
 * counted, not errored (the shared client reports it as {@code NOOP}).
 */
public final class DocumentDatasetWriter implements VersionedDatasetWriter {

  private final DocumentBasedWriteClient writeClient;

  /** Writes rejected by the version fence — stale by the time they reached the store. */
  private final LongAdder fencedWrites = new LongAdder();

  public DocumentDatasetWriter(final DocumentBasedWriteClient writeClient) {
    this.writeClient = writeClient;
  }

  /** Writes rejected by the version fence since this writer opened (zero outside rebalances). */
  public long fencedWrites() {
    return fencedWrites.sum();
  }

  @Override
  public void upsertCell(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long windowStart,
      final long windowSize,
      final byte[] compositeAccumulator,
      final WriteVersion version) {
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> grain = dataset.grain().columns();
    for (int i = 0; i < grain.size(); i++) {
      doc.put(DocumentCubeNames.field(grain.get(i).name()), key.get(i));
    }
    doc.put(DocumentCubeNames.WINDOW_START, windowStart);
    doc.put(DocumentCubeNames.WINDOW_SIZE, windowSize);
    putMeterFields(doc, dataset, compositeAccumulator);

    final String id = DocumentCubeNames.cellDocId(key, windowStart, windowSize);
    index(DocumentCubeNames.datasetIndex(dataset.cubeId()), id, doc, version);
  }

  @Override
  public void upsertSnapshotRow(
      final CompiledDataset dataset,
      final DimensionKey key,
      final long sampleTime,
      final byte[] compositeAccumulator,
      final WriteVersion version) {
    // Snapshot cubes are additive-only (validated at compile time), so every slot decomposes into
    // native numeric fields — the row carries the key's cumulative absolutes as of sample_time.
    final Map<String, Object> doc = new LinkedHashMap<>();
    final List<DimensionColumn> grain = dataset.grain().columns();
    for (int i = 0; i < grain.size(); i++) {
      doc.put(DocumentCubeNames.field(grain.get(i).name()), key.get(i));
    }
    doc.put(DocumentCubeNames.SAMPLE_TIME, sampleTime);
    putMeterFields(doc, dataset, compositeAccumulator);

    final String id = DocumentCubeNames.snapshotDocId(key, sampleTime);
    index(DocumentCubeNames.snapshotIndex(dataset.cubeId()), id, doc, version);
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
    index(DocumentCubeNames.rowIndex(table.cubeId()), rowKey, doc, version);
  }

  /**
   * Adds every meter's serving fields from the composite accumulator (ADR 0009 — all slots of the
   * row in one document). A pushable (additive) meter decomposes its slot into native numeric
   * fields (the composite aggregation reduces them); a sketch/summary writes its app-mergeable blob
   * (streamed + merged) plus a finalized scalar for the DIRECT fast path. An absent slot (older
   * layout) writes the meter's empty accumulator.
   */
  private static void putMeterFields(
      final Map<String, Object> doc,
      final CompiledDataset dataset,
      final byte[] compositeAccumulator) {
    final List<CompiledMeter> meters = dataset.meters();
    final List<byte[]> slots =
        CompositeAccumulatorValue.slotBytes(compositeAccumulator, meters.size());
    for (int slot = 0; slot < meters.size(); slot++) {
      final CompiledMeter meter = meters.get(slot);
      final byte[] slotBytes = slots.get(slot);
      final Optional<PushdownSpec<?, ?>> spec = meter.pushdown();
      if (spec.isPresent()) {
        final List<Object> values = decompose(meter.bound(), slotBytes);
        final List<PushdownColumn> columns = spec.get().columns();
        for (int i = 0; i < columns.size(); i++) {
          doc.put(
              DocumentCubeNames.pushdownField(meter.meterName(), columns.get(i).suffix()),
              values.get(i));
        }
      } else {
        final byte[] blob = slotBytes == null ? emptySlot(meter.bound()) : slotBytes;
        doc.put(DocumentCubeNames.blobField(meter.meterName()), DocumentCubeNames.encode(blob));
        doc.put(
            DocumentCubeNames.valueField(meter.meterName()),
            SketchScalar.of(finalized(meter.bound(), blob)));
      }
    }
  }

  /** The fenced upsert every document write goes through: external_gte on the packed version. */
  private void index(
      final String index,
      final String id,
      final Map<String, Object> doc,
      final WriteVersion version) {
    doc.put(DocumentCubeNames.DOC_KEY, id); // sortable copy of the id, for search_after streaming
    // The version fields are also stored as plain fields so documents stay diagnosable — the
    // enforcing copy is the packed external version on the request.
    doc.put(DocumentCubeNames.VER_EPOCH, version.epoch());
    doc.put(DocumentCubeNames.VER_OFFSET, version.offset());
    final SearchWriteResponse response =
        writeClient.index(
            RequestBuilders.<Map<String, Object>>indexRequest(
                r ->
                    r.index(index)
                        .id(id)
                        .document(doc)
                        .version(DocumentVersions.pack(version))
                        .versionType(SearchIndexRequest.VersionType.EXTERNAL_GTE)));
    if (response.result() == SearchWriteResponse.Result.NOOP) {
      // The fence working, not an error: the store already holds an equal-or-newer row.
      fencedWrites.increment();
    }
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
  public void flush() {
    // each index() is applied on call; a bulk/refresh batching pass is a later optimization
  }

  @Override
  public void close() {
    // the client is owned by the store
  }
}

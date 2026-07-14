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
import io.camunda.search.clients.core.SearchBulkIndexRequest;
import io.camunda.search.clients.core.SearchBulkResponse;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.exception.CamundaSearchException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

/**
 * The document serving {@link DatasetWriter}: each write is a full index of the current value with
 * a deterministic id, which is an idempotent overwrite (the serving store holds current values, not
 * deltas). A cube cell is <b>one document</b> carrying every meter's serving columns from the
 * composite accumulator (ADR 0009 — one writer per row, one atomic upsert, never torn): an additive
 * meter's pushdown columns as native numeric fields, a sketch's app-mergeable blob plus its
 * finalized scalar. Snapshot rows and projected rows are likewise one document each.
 *
 * <p>Writes are <em>staged</em>, not indexed on call: {@link #flush()} — the batch boundary the
 * staged-cut protocol drives at the commit barrier — sends everything staged since the last flush
 * as {@code _bulk} requests of at most {@link #MAX_BULK_ITEMS} items and only returns once the
 * store has answered, keeping produce-before-commit intact. Staging dedups by document id, keeping
 * the newest write (the staging writer may upsert the same cell repeatedly between cuts; within one
 * writer versions grow monotonically, so the newest write also carries the highest version), which
 * both preserves last-writer-wins and shrinks the bulk.
 *
 * <p>The write fence is the store's external versioning: every write carries the {@link
 * WriteVersion} packed into one order-preserving {@code long} (see {@link DocumentVersions}) with
 * {@code external_gte} semantics, so the store itself rejects a fenced zombie's stale overwrite —
 * an equal-or-newer version applies (idempotent replay), an older one affects nothing and is
 * counted, not errored (the shared client reports the item as {@code NOOP}). Any other item failure
 * fails the flush — the cut fails and retries, and the fence makes the replay idempotent.
 *
 * <p>Not thread-safe: one writer per owner. The cut protocol's single-flight handoff provides the
 * happens-before edges when staging (actor thread) and flushing (IO thread) alternate.
 */
public final class DocumentDatasetWriter implements VersionedDatasetWriter {

  /**
   * Upper bound of documents per {@code _bulk} request: a flush larger than this is split into
   * consecutive chunks. 1000 items keeps a request of typical cell documents in the low
   * single-digit megabytes — comfortably inside the stores' default {@code http.max_content_length}
   * (100mb) — while still amortizing the per-request overhead that motivated bulking.
   */
  static final int MAX_BULK_ITEMS = 1_000;

  private final DocumentBasedWriteClient writeClient;

  /**
   * The staged documents of the current batch, keyed by (index, id) so a re-upsert of the same row
   * replaces the older staged write; insertion-ordered, so distinct rows flush in write order.
   */
  private final Map<DocumentKey, SearchIndexRequest<Map<String, Object>>> staged =
      new LinkedHashMap<>();

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
    stage(DocumentCubeNames.datasetIndex(dataset.cubeId()), id, doc, version);
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
    stage(DocumentCubeNames.snapshotIndex(dataset.cubeId()), id, doc, version);
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
    stage(DocumentCubeNames.rowIndex(table.cubeId()), rowKey, doc, version);
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

  /**
   * The fenced upsert every document write goes through: external_gte on the packed version. The
   * write is only <em>staged</em> here — a re-upsert of the same (index, id) replaces the older
   * staged write — and reaches the store at the next {@link #flush()}.
   */
  private void stage(
      final String index,
      final String id,
      final Map<String, Object> doc,
      final WriteVersion version) {
    doc.put(DocumentCubeNames.DOC_KEY, id); // sortable copy of the id, for search_after streaming
    // The version fields are also stored as plain fields so documents stay diagnosable — the
    // enforcing copy is the packed external version on the request.
    doc.put(DocumentCubeNames.VER_EPOCH, version.epoch());
    doc.put(DocumentCubeNames.VER_OFFSET, version.offset());
    staged.put(
        new DocumentKey(index, id),
        RequestBuilders.<Map<String, Object>>indexRequest(
            r ->
                r.index(index)
                    .id(id)
                    .document(doc)
                    .version(DocumentVersions.pack(version))
                    .versionType(SearchIndexRequest.VersionType.EXTERNAL_GTE)));
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

  /**
   * Sends everything staged since the last flush as {@code _bulk} requests of at most {@link
   * #MAX_BULK_ITEMS} items each. A per-item version conflict is the fence working — counted, never
   * thrown. Any other item failure (or a failed bulk request itself) throws so the cut fails and
   * retries; the staged batch is kept, and the retry's re-staged writes overwrite it idempotently.
   */
  @Override
  public void flush() {
    if (staged.isEmpty()) {
      return;
    }
    final List<SearchIndexRequest<Map<String, Object>>> pending = new ArrayList<>(staged.values());
    for (int from = 0; from < pending.size(); from += MAX_BULK_ITEMS) {
      sendBulk(pending.subList(from, Math.min(from + MAX_BULK_ITEMS, pending.size())));
    }
    staged.clear();
  }

  private void sendBulk(final List<SearchIndexRequest<Map<String, Object>>> chunk) {
    final SearchBulkResponse response = writeClient.bulk(new SearchBulkIndexRequest<>(chunk));
    SearchBulkResponse.Item firstFailure = null;
    for (final SearchBulkResponse.Item item : response.items()) {
      switch (item.result()) {
        // The fence working, not an error: the store already holds an equal-or-newer row.
        case NOOP -> fencedWrites.increment();
        case FAILED -> firstFailure = firstFailure == null ? item : firstFailure;
        case APPLIED -> {}
      }
    }
    if (firstFailure != null) {
      throw new CamundaSearchException(
          "Bulk serving flush failed for document '"
              + firstFailure.id()
              + "' in index '"
              + firstFailure.index()
              + "': "
              + firstFailure.error());
    }
  }

  @Override
  public void close() {
    // the client is owned by the store
  }

  /** The staging key: one staged write per document, the newest wins. */
  private record DocumentKey(String index, String id) {}
}

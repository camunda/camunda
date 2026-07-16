/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import java.nio.ByteOrder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * One group's durable {@code (window, key) -> accumulator} cells in a shared byte-keyed {@link
 * KeyValueStore} — the persistence layout both segment aggregations share, extracted so the two
 * sides of the shuffle encode, scan and recover cells identically.
 *
 * <p><b>The byte layout is durable identity — never change it.</b> A cell row is keyed {@code
 * group(int) ++ windowStart(long) ++ keyCodec(key)} with big-endian framing; the optional meta row
 * is keyed by the bare 4-byte {@code group}, shorter than any cell key (which is at least {@code
 * group ++ windowStart}), so it never collides with a cell in the shared store. The {@code group}
 * prefix lets several operators share one store and scan only their own rows. Deployed state was
 * written under exactly these bytes; changing the framing orphans it (pinned by {@code
 * GroupedCellLayoutTest}).
 *
 * <p>Encode and decode go through reused flyweights and a growable scratch buffer, so the write
 * path allocates nothing per call beyond what the codecs themselves produce. Like the operators
 * that own it, an instance is single-writer — never share one across threads.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
final class GroupedCellStore<K, ACC> {

  private static final int WINDOW_START_OFFSET = Integer.BYTES;
  private static final int KEY_OFFSET = Integer.BYTES + Long.BYTES;

  private final KeyValueStore<DbBytes, DbBytes> store;
  private final RecordValue<K> keyCodec;
  private final RecordValue<ACC> accCodec;

  /** The bare big-endian group — the prefix of every row and the key of the meta row. */
  private final DbBytes groupKey = new DbBytes();

  private final DbBytes cellKey = new DbBytes();
  private final DbBytes cellValue = new DbBytes();
  // Reused encode scratch; the group framing is written once and stays as the constant prefix.
  private final ExpandableArrayBuffer keyScratch = new ExpandableArrayBuffer(64);
  private final ExpandableArrayBuffer valueScratch = new ExpandableArrayBuffer(64);

  GroupedCellStore(
      final int group,
      final KeyValueStore<DbBytes, DbBytes> store,
      final RecordValue<K> keyCodec,
      final RecordValue<ACC> accCodec) {
    this.store = store;
    this.keyCodec = keyCodec;
    this.accCodec = accCodec;
    final byte[] groupBytes = new byte[Integer.BYTES];
    new UnsafeBuffer(groupBytes).putInt(0, group, ByteOrder.BIG_ENDIAN);
    groupKey.wrapBytes(groupBytes);
    keyScratch.putInt(0, group, ByteOrder.BIG_ENDIAN);
  }

  /** Visits every durable cell of this group; a meta row (if any) is skipped. */
  void scanCells(final BiConsumer<Windowed<K>, ACC> cells) {
    scan(cells, meta -> {});
  }

  /**
   * Visits every durable cell of this group, handing the meta row's value bytes (if the row is
   * present) to {@code meta}. The recover path — called once, at construction of the owner.
   */
  void scan(final BiConsumer<Windowed<K>, ACC> cells, final Consumer<byte[]> meta) {
    store.prefixScan(
        groupKey,
        (key, value) -> {
          final DirectBuffer raw = key.getDirectBuffer();
          if (raw.capacity() == Integer.BYTES) {
            meta.accept(value.getBytes());
            return;
          }
          final long windowStart = raw.getLong(WINDOW_START_OFFSET, ByteOrder.BIG_ENDIAN);
          keyCodec.wrap(raw, KEY_OFFSET, raw.capacity() - KEY_OFFSET);
          cells.accept(
              new Windowed<>(keyCodec.value(), windowStart), accCodec.fromBytes(value.getBytes()));
        });
  }

  /** Upserts {@code cell}'s row, serializing {@code value} through the accumulator codec. */
  void put(final Windowed<K> cell, final ACC value) {
    accCodec.wrapValue(value);
    final int length = accCodec.write(valueScratch, 0);
    encodeCellKey(cell);
    // Wrap the flyweight's buffer over the exact scratch range; DbBytes#wrapBytes would take the
    // whole backing array and a fresh copy per call would defeat the reused scratch.
    cellValue.getDirectBuffer().wrap(valueScratch.byteArray(), 0, length);
    store.put(cellKey, cellValue);
  }

  /** Upserts {@code cell}'s row from an already-serialized accumulator (e.g. a flush's bytes). */
  void putSerialized(final Windowed<K> cell, final byte[] serialized) {
    encodeCellKey(cell);
    cellValue.wrapBytes(serialized);
    store.put(cellKey, cellValue);
  }

  /** Deletes {@code cell}'s row if present. */
  void delete(final Windowed<K> cell) {
    encodeCellKey(cell);
    store.delete(cellKey);
  }

  /**
   * A stable copy of {@code cell}'s row key bytes, safe to retain past the next call — unlike
   * {@link #encodeCellKey}, which wraps a flyweight over reused encode scratch that the very next
   * call overwrites. Used to build changelog records (streaming ADR 0009), whose keys must outlive
   * this call: they are collected into a batch before publishing.
   */
  byte[] encodeCellKeyBytes(final Windowed<K> cell) {
    encodeCellKey(cell);
    final DirectBuffer buffer = cellKey.getDirectBuffer();
    final byte[] copy = new byte[buffer.capacity()];
    buffer.getBytes(0, copy);
    return copy;
  }

  /** Upserts the group's meta row (the bare-group key); the value layout is the caller's. */
  void putMeta(final byte[] value) {
    cellValue.wrapBytes(value);
    store.put(groupKey, cellValue);
  }

  /** Cell key: {@code group ++ windowStart ++ codec(key)}, big-endian framing. */
  private void encodeCellKey(final Windowed<K> cell) {
    keyScratch.putLong(WINDOW_START_OFFSET, cell.windowStart(), ByteOrder.BIG_ENDIAN);
    keyCodec.wrapValue(cell.key());
    final int keyLength = keyCodec.write(keyScratch, KEY_OFFSET);
    cellKey.getDirectBuffer().wrap(keyScratch.byteArray(), 0, KEY_OFFSET + keyLength);
  }
}

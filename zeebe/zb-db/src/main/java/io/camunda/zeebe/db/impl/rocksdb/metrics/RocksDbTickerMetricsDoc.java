/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.metrics;

import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import org.rocksdb.TickerType;

/**
 * Documents the RocksDB tickers exported to Micrometer.
 *
 * <p>Unlike {@link RocksDbMetricsDoc} (DB properties) and {@link RocksDbIoStallMetricsDoc} ({@code
 * rocksdb.cfstats}), tickers are only recorded when a RocksDB {@code Statistics} object is attached
 * to the database. That is opt-in via {@code RocksDbConfiguration#setStatisticsEnabled}, so all
 * meters here are absent unless statistics are enabled.
 *
 * <p>The tickers below answer questions the property-based metrics cannot: whether reads are served
 * from the block cache or from the filesystem, how much of the read path is iteration rather than
 * point lookups, and whether the bloom filters are actually filtering anything.
 *
 * <p>Every ticker is a counter that RocksDB accumulates itself since the database was opened. We
 * mirror the latest reading into a gauge rather than a Micrometer counter, matching {@link
 * RocksDbIoStallMetricsDoc}: it resets to zero on database reopen, and rates are derived in the
 * query layer.
 */
public enum RocksDbTickerMetricsDoc implements RocksDbMeterDoc {
  // ---- block cache ----
  BLOCK_CACHE_HIT(
      TickerType.BLOCK_CACHE_HIT,
      "rocksdb.cache",
      "hit",
      "Cumulative number of block cache lookups served from the cache"),
  BLOCK_CACHE_MISS(
      TickerType.BLOCK_CACHE_MISS,
      "rocksdb.cache",
      "miss",
      "Cumulative number of block cache lookups that had to read from disk"),
  BLOCK_CACHE_DATA_HIT(
      TickerType.BLOCK_CACHE_DATA_HIT,
      "rocksdb.cache",
      "data.hit",
      "Cumulative number of data block lookups served from the block cache"),
  BLOCK_CACHE_DATA_MISS(
      TickerType.BLOCK_CACHE_DATA_MISS,
      "rocksdb.cache",
      "data.miss",
      "Cumulative number of data block lookups that had to read from disk"),
  BLOCK_CACHE_INDEX_HIT(
      TickerType.BLOCK_CACHE_INDEX_HIT,
      "rocksdb.cache",
      "index.hit",
      "Cumulative number of index block lookups served from the block cache"),
  BLOCK_CACHE_INDEX_MISS(
      TickerType.BLOCK_CACHE_INDEX_MISS,
      "rocksdb.cache",
      "index.miss",
      "Cumulative number of index block lookups that had to read from disk"),
  BLOCK_CACHE_FILTER_HIT(
      TickerType.BLOCK_CACHE_FILTER_HIT,
      "rocksdb.cache",
      "filter.hit",
      "Cumulative number of filter block lookups served from the block cache"),
  BLOCK_CACHE_FILTER_MISS(
      TickerType.BLOCK_CACHE_FILTER_MISS,
      "rocksdb.cache",
      "filter.miss",
      "Cumulative number of filter block lookups that had to read from disk"),
  BLOCK_CACHE_BYTES_READ(
      TickerType.BLOCK_CACHE_BYTES_READ,
      "rocksdb.cache",
      "bytes.read",
      "Cumulative bytes read out of the block cache"),
  BLOCK_CACHE_BYTES_WRITE(
      TickerType.BLOCK_CACHE_BYTES_WRITE,
      "rocksdb.cache",
      "bytes.write",
      "Cumulative bytes inserted into the block cache"),

  // ---- bloom filters ----
  BLOOM_FILTER_USEFUL(
      TickerType.BLOOM_FILTER_USEFUL,
      "rocksdb.bloom",
      "useful",
      "Cumulative number of times the whole-key bloom filter avoided reading an SST block"),
  BLOOM_FILTER_FULL_POSITIVE(
      TickerType.BLOOM_FILTER_FULL_POSITIVE,
      "rocksdb.bloom",
      "full.positive",
      "Cumulative number of times the whole-key bloom filter reported a match"),
  BLOOM_FILTER_FULL_TRUE_POSITIVE(
      TickerType.BLOOM_FILTER_FULL_TRUE_POSITIVE,
      "rocksdb.bloom",
      "full.true.positive",
      "Cumulative number of whole-key bloom filter matches where the key really existed"),
  BLOOM_FILTER_PREFIX_CHECKED(
      TickerType.BLOOM_FILTER_PREFIX_CHECKED,
      "rocksdb.bloom",
      "prefix.checked",
      "Cumulative number of prefix bloom filter lookups. Compare against prefix.useful to see whether the prefix extractor is selective enough to be worth its memory"),
  BLOOM_FILTER_PREFIX_USEFUL(
      TickerType.BLOOM_FILTER_PREFIX_USEFUL,
      "rocksdb.bloom",
      "prefix.useful",
      "Cumulative number of times the prefix bloom filter avoided reading an SST block"),
  BLOOM_FILTER_PREFIX_TRUE_POSITIVE(
      TickerType.BLOOM_FILTER_PREFIX_TRUE_POSITIVE,
      "rocksdb.bloom",
      "prefix.true.positive",
      "Cumulative number of prefix bloom filter matches where a matching key really existed"),

  // ---- point reads ----
  NUMBER_KEYS_READ(
      TickerType.NUMBER_KEYS_READ, "rocksdb.reads", "keys", "Cumulative number of keys read"),
  BYTES_READ(
      TickerType.BYTES_READ, "rocksdb.reads", "bytes", "Cumulative bytes read by point gets"),
  MEMTABLE_HIT(
      TickerType.MEMTABLE_HIT,
      "rocksdb.reads",
      "memtable.hit",
      "Cumulative number of gets served from a memtable"),
  MEMTABLE_MISS(
      TickerType.MEMTABLE_MISS,
      "rocksdb.reads",
      "memtable.miss",
      "Cumulative number of gets that had to look past the memtables"),
  GET_HIT_L0(
      TickerType.GET_HIT_L0, "rocksdb.reads", "hit.l0", "Cumulative number of gets served from L0"),
  GET_HIT_L1(
      TickerType.GET_HIT_L1, "rocksdb.reads", "hit.l1", "Cumulative number of gets served from L1"),
  GET_HIT_L2_AND_UP(
      TickerType.GET_HIT_L2_AND_UP,
      "rocksdb.reads",
      "hit.l2andup",
      "Cumulative number of gets served from L2 or a deeper level"),

  // ---- iteration ----
  NUMBER_DB_SEEK(
      TickerType.NUMBER_DB_SEEK,
      "rocksdb.iterators",
      "seek",
      "Cumulative number of iterator seeks"),
  NUMBER_DB_SEEK_FOUND(
      TickerType.NUMBER_DB_SEEK_FOUND,
      "rocksdb.iterators",
      "seek.found",
      "Cumulative number of iterator seeks that landed on a valid entry"),
  NUMBER_DB_NEXT(
      TickerType.NUMBER_DB_NEXT,
      "rocksdb.iterators",
      "next",
      "Cumulative number of forward iterator steps"),
  NUMBER_DB_NEXT_FOUND(
      TickerType.NUMBER_DB_NEXT_FOUND,
      "rocksdb.iterators",
      "next.found",
      "Cumulative number of forward iterator steps that landed on a valid entry"),
  NUMBER_DB_PREV(
      TickerType.NUMBER_DB_PREV,
      "rocksdb.iterators",
      "prev",
      "Cumulative number of backward iterator steps"),
  NUMBER_ITER_SKIP(
      TickerType.NUMBER_ITER_SKIP,
      "rocksdb.iterators",
      "skip",
      "Cumulative number of internal keys skipped during iteration, for example tombstones and shadowed versions"),
  NUMBER_OF_RESEEKS_IN_ITERATION(
      TickerType.NUMBER_OF_RESEEKS_IN_ITERATION,
      "rocksdb.iterators",
      "reseek",
      "Cumulative number of times iteration had to re-seek instead of stepping forward"),
  ITER_BYTES_READ(
      TickerType.ITER_BYTES_READ,
      "rocksdb.iterators",
      "bytes.read",
      "Cumulative bytes read through iterators. Compare against reads.bytes to see how much of the read path is iteration"),
  NO_ITERATOR_CREATED(
      TickerType.NO_ITERATOR_CREATED,
      "rocksdb.iterators",
      "created",
      "Cumulative number of iterators created. Each one builds a merging iterator over the memtables and L0 files, so this is a direct cost driver"),
  NO_ITERATOR_DELETED(
      TickerType.NO_ITERATOR_DELETED,
      "rocksdb.iterators",
      "deleted",
      "Cumulative number of iterators released. A persistent gap to created means iterators are being leaked"),
  NUMBER_SUPERVERSION_ACQUIRES(
      TickerType.NUMBER_SUPERVERSION_ACQUIRES,
      "rocksdb.iterators",
      "superversion.acquires",
      "Cumulative number of superversion acquisitions, which happen once per iterator creation and pin the current memtable and file set"),
  NUMBER_SUPERVERSION_RELEASES(
      TickerType.NUMBER_SUPERVERSION_RELEASES,
      "rocksdb.iterators",
      "superversion.releases",
      "Cumulative number of superversion releases"),
  NUMBER_SUPERVERSION_CLEANUPS(
      TickerType.NUMBER_SUPERVERSION_CLEANUPS,
      "rocksdb.iterators",
      "superversion.cleanups",
      "Cumulative number of superversion cleanups"),

  // ---- writes ----
  NUMBER_KEYS_WRITTEN(
      TickerType.NUMBER_KEYS_WRITTEN,
      "rocksdb.writes",
      "keys",
      "Cumulative number of keys written"),
  BYTES_WRITTEN(TickerType.BYTES_WRITTEN, "rocksdb.writes", "bytes", "Cumulative bytes written"),
  WRITE_DONE_BY_SELF(
      TickerType.WRITE_DONE_BY_SELF,
      "rocksdb.writes",
      "done.by.self",
      "Cumulative number of write batches this thread applied itself rather than handing to a group leader"),
  WRITE_DONE_BY_OTHER(
      TickerType.WRITE_DONE_BY_OTHER,
      "rocksdb.writes",
      "done.by.other",
      "Cumulative number of write batches applied on this thread's behalf by a group leader"),
  STALL_MICROS(
      TickerType.STALL_MICROS,
      "rocksdb.writes",
      "stall.micros",
      "Cumulative microseconds writes were stalled waiting on flushes or compactions"),

  // ---- background work ----
  COMPACT_READ_BYTES(
      TickerType.COMPACT_READ_BYTES,
      "rocksdb.compaction",
      "read.bytes",
      "Cumulative bytes read by compaction"),
  COMPACT_WRITE_BYTES(
      TickerType.COMPACT_WRITE_BYTES,
      "rocksdb.compaction",
      "write.bytes",
      "Cumulative bytes written by compaction"),
  FLUSH_WRITE_BYTES(
      TickerType.FLUSH_WRITE_BYTES,
      "rocksdb.compaction",
      "flush.write.bytes",
      "Cumulative bytes written by memtable flushes"),
  COMPACTION_KEY_DROP_OBSOLETE(
      TickerType.COMPACTION_KEY_DROP_OBSOLETE,
      "rocksdb.compaction",
      "key.drop.obsolete",
      "Cumulative number of keys dropped by compaction because they were overwritten or deleted"),

  // ---- file access ----
  NO_FILE_OPENS(
      TickerType.NO_FILE_OPENS, "rocksdb.files", "opens", "Cumulative number of SST file opens"),
  NO_FILE_ERRORS(
      TickerType.NO_FILE_ERRORS,
      "rocksdb.files",
      "errors",
      "Cumulative number of file read errors"),
  LAST_LEVEL_READ_COUNT(
      TickerType.LAST_LEVEL_READ_COUNT,
      "rocksdb.files",
      "last.level.reads",
      "Cumulative number of reads served from the last (largest, compressed) level"),
  NON_LAST_LEVEL_READ_COUNT(
      TickerType.NON_LAST_LEVEL_READ_COUNT,
      "rocksdb.files",
      "non.last.level.reads",
      "Cumulative number of reads served from a level above the last one");

  private static final String ZEEBE_NAMESPACE = "zeebe";

  private final TickerType ticker;
  private final String namespace;
  private final String suffix;
  private final String description;

  RocksDbTickerMetricsDoc(
      final TickerType ticker,
      final String namespace,
      final String suffix,
      final String description) {
    this.ticker = ticker;
    this.namespace = namespace;
    this.suffix = suffix;
    this.description = description;
  }

  public TickerType ticker() {
    return ticker;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getName() {
    return ZEEBE_NAMESPACE + "." + namespace + "." + suffix;
  }

  @Override
  public Type getType() {
    return Type.GAUGE;
  }

  @Override
  public KeyName[] getAdditionalKeyNames() {
    return PartitionKeyNames.values();
  }

  /** The name of the underlying RocksDB ticker. */
  @Override
  public String propertyName() {
    return ticker.name();
  }

  @Override
  public String namespace() {
    return namespace;
  }
}

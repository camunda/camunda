/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.rocksdb;

import io.camunda.microbenchmarks.rocksdb.WorkloadConfig.DeleteMode;
import io.camunda.microbenchmarks.rocksdb.WorkloadConfig.ReadMode;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbResources.RuntimeInfo;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Properties;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.agrona.collections.LongArrayQueue;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.FlushOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Slice;
import org.rocksdb.Statistics;
import org.rocksdb.TickerType;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

/**
 * A plain (non-JMH) RocksDB workload that reproduces the access pattern of Zeebe's job queue on a
 * DB opened through {@link ZeebeRocksDbFactory}, i.e. with the exact production options.
 *
 * <p>Reads and writes go through the same primitives that {@code TransactionalColumnFamily} uses
 * (one reused {@link WriteBatchWithIndex} per transaction, a fresh base iterator wrapped by the
 * batch for every prefix scan, keys prefixed by the 8-byte Zeebe column family id) but bypass the
 * typed {@code ColumnFamily} API, so that variants which the production code does not support yet
 * (upper-bounded iterators, single deletes) can be compared side by side against the same state.
 *
 * <p>Per job: create (JOBS + JOB_ACTIVATABLE_BY_PRIORITY), activate the oldest activatable job once
 * the backlog is exceeded (delete activatable entry, add deadline), complete the oldest in-flight
 * job (delete job and deadline). In between, {@code pollsPerJob} activation polls are issued
 * round-robin over all job types and the deadline column family is scanned periodically like the
 * job timeout checker does. Results are appended to a CSV, one row per window.
 */
public final class JobQueueWorkload implements AutoCloseable {

  static {
    RocksDB.loadLibrary();
  }

  private static final int CF_JOBS = ZbColumnFamilies.JOBS.getValue();
  private static final int CF_DEADLINES = ZbColumnFamilies.JOB_DEADLINES.getValue();
  private static final int CF_ACTIVATABLE = ZbColumnFamilies.JOB_ACTIVATABLE_BY_PRIORITY.getValue();
  // far away from any real column family, only used to put realistic weight on the LSM tree
  private static final int CF_BACKGROUND_BASE = 10_000;
  private static final int PRIORITY_ZERO_INVERTED = Integer.MAX_VALUE;
  private static final byte[] TENANT = "<default>".getBytes(StandardCharsets.UTF_8);
  private static final byte[] NIL = {0};

  private static final String[] PROPERTIES = {
    "rocksdb.num-files-at-level0",
    "rocksdb.num-files-at-level1",
    "rocksdb.num-files-at-level2",
    "rocksdb.num-files-at-level3",
    "rocksdb.estimate-num-keys",
    "rocksdb.num-entries-active-mem-table",
    "rocksdb.num-deletes-active-mem-table",
    "rocksdb.num-entries-imm-mem-tables",
    "rocksdb.num-deletes-imm-mem-tables",
    "rocksdb.num-immutable-mem-table",
    "rocksdb.cur-size-all-mem-tables",
    "rocksdb.block-cache-usage",
    "rocksdb.total-sst-files-size",
    "rocksdb.estimate-pending-compaction-bytes",
  };

  private static final TickerType[] TICKERS = {
    TickerType.NUMBER_DB_SEEK,
    TickerType.NUMBER_DB_NEXT,
    TickerType.NUMBER_ITER_SKIP,
    TickerType.BLOCK_CACHE_HIT,
    TickerType.BLOCK_CACHE_MISS,
    TickerType.BLOCK_CACHE_DATA_MISS,
    TickerType.BLOCK_CACHE_INDEX_MISS,
    TickerType.BLOCK_CACHE_FILTER_MISS,
    TickerType.BYTES_READ,
    TickerType.COMPACT_READ_BYTES,
    TickerType.COMPACT_WRITE_BYTES,
    TickerType.FLUSH_WRITE_BYTES,
    TickerType.COMPACTION_KEY_DROP_NEWER_ENTRY,
    TickerType.COMPACTION_KEY_DROP_OBSOLETE,
  };

  private final WorkloadConfig config;
  private final SplittableRandom random;
  private final RocksDbResources resources;
  private final ZeebeTransactionDb<ZbColumnFamilies> zeebeDb;
  private final RocksDB db;
  private final ColumnFamilyHandle handle;
  private final Statistics statistics;
  private final ReadOptions prefixReadOptions;
  private final ReadOptions pointReadOptions;
  private final WriteOptions writeOptions;
  private final WriteBatchWithIndex transaction = new WriteBatchWithIndex(true);

  private final byte[][] typePrefixes;
  private final byte[][] typeUpperBounds;
  private final byte[][] typePriorityZeroSeekKeys;
  private final int[] churnTypeIndexes;
  private final LongArrayQueue[] activatable;
  private final LongArrayQueue inflightJobs = new LongArrayQueue(Long.MIN_VALUE);
  private final LongArrayQueue inflightDeadlines = new LongArrayQueue(Long.MIN_VALUE);
  private final byte[] jobValue;
  private final byte[] deadlineScanStart;
  private final byte[] deadlineScanUpperBound;

  private final LatencyRecorder pollLatency = new LatencyRecorder();
  private final LatencyRecorder iteratorCreateLatency = new LatencyRecorder();
  private final LatencyRecorder getLatency = new LatencyRecorder();
  private final LatencyRecorder writeLatency = new LatencyRecorder();
  private final LatencyRecorder deadlineScanLatency = new LatencyRecorder();
  private final EnumMap<TickerType, Long> lastTickers = new EnumMap<>(TickerType.class);

  private long keyCounter;
  private long lastDeadlineScan;
  // per type: no live activatable entry has a smaller job key (only used with seekHint)
  private final long[] lowWatermarks;
  private int nextPollType;
  private int nextChurnType;
  private long jobsCreated;
  private long jobsActivated;
  private long jobsPolledVisited;

  private JobQueueWorkload(final WorkloadConfig config) throws Exception {
    this.config = config;
    random = new SplittableRandom(config.seed());

    final var rocksDbConfiguration =
        new RocksDbConfiguration()
            .setMemoryLimit(config.memoryLimit())
            .setMemoryAllocationStrategy(config.memoryStrategy())
            .setSstPartitioningEnabled(config.sstPartitioning())
            .setStatisticsEnabled(config.statistics())
            .setCompactOnDeletion(config.compactOnDeletion());
    resources = RocksDbResources.of(rocksDbConfiguration, new RuntimeInfo(config.partitions()));
    final var cfOptions = new Properties();
    if (config.preTuningDefaults()) {
      // the defaults before tuning for tombstones: write buffers sized by the budget alone and
      // merged three at a time before flushing (compact-on-deletion is resolved by WorkloadConfig)
      final long budgetSized =
          Math.round(
              (double) resources.writeBufferBudgetPerPartition()
                  / rocksDbConfiguration.getMaxWriteBufferNumber()
                  * 0.85);
      cfOptions.setProperty("write_buffer_size", Long.toString(budgetSized));
      cfOptions.setProperty("min_write_buffer_number_to_merge", "3");
    }
    // explicit cf.* arguments win over the pre-tuning defaults
    cfOptions.putAll(config.cfOptions());
    rocksDbConfiguration.setColumnFamilyOptions(cfOptions);
    final var factory =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
            rocksDbConfiguration,
            new ConsistencyChecksSettings(false, false),
            new AccessMetricsConfiguration(Kind.NONE),
            SimpleMeterRegistry::new,
            resources);
    zeebeDb = factory.createDb(config.dbDir().toFile());
    db = field(ZeebeTransactionDb.class, zeebeDb, "rocksDB");
    handle = field(ZeebeTransactionDb.class, zeebeDb, "defaultHandle");
    final Object exporter = field(ZeebeTransactionDb.class, zeebeDb, "metricExporter");
    statistics = field(exporter.getClass(), exporter, "statistics");

    // mirrors PrefixReadOptions.readOptions() and the plain ReadOptions used for gets
    prefixReadOptions =
        new ReadOptions().setPrefixSameAsStart(true).setTotalOrderSeek(false).setReadaheadSize(0);
    pointReadOptions = new ReadOptions();
    writeOptions = new WriteOptions().setDisableWAL(true);

    final int typeCount = config.pollTypes();
    typePrefixes = new byte[typeCount][];
    typeUpperBounds = new byte[typeCount][];
    typePriorityZeroSeekKeys = new byte[typeCount][];
    lowWatermarks = new long[typeCount];
    for (int i = 0; i < typeCount; i++) {
      final var type = "job-type-%04d".formatted(i).getBytes(StandardCharsets.UTF_8);
      final var prefix = ByteBuffer.allocate(Long.BYTES + Integer.BYTES + type.length);
      prefix.putLong(CF_ACTIVATABLE).putInt(type.length).put(type);
      typePrefixes[i] = prefix.array();
      typeUpperBounds[i] = successor(typePrefixes[i]);
      typePriorityZeroSeekKeys[i] =
          ByteBuffer.allocate(typePrefixes[i].length + Integer.BYTES + Long.BYTES + Integer.BYTES)
              .put(typePrefixes[i])
              .putInt(PRIORITY_ZERO_INVERTED)
              .putLong(0)
              .putInt(0)
              .array();
    }

    // spread the churning types evenly so that empty types sort before and after them
    churnTypeIndexes = new int[config.churnTypes()];
    activatable = new LongArrayQueue[config.churnTypes()];
    for (int i = 0; i < churnTypeIndexes.length; i++) {
      churnTypeIndexes[i] = (int) ((i + 0.5) * typeCount / churnTypeIndexes.length);
      activatable[i] = new LongArrayQueue(Long.MIN_VALUE);
    }

    jobValue = new byte[config.jobValueSize()];
    random.nextBytes(jobValue);
    deadlineScanStart = ByteBuffer.allocate(Long.BYTES).putLong(CF_DEADLINES).array();
    deadlineScanUpperBound = successor(deadlineScanStart);
    // keys are generated like Zeebe's DbKeyGenerator: partition id in the upper bits, a counter
    // that
    // only moves forward below it; other records (instances, element instances, variables, ...)
    // consume keys too, so consecutive job keys are increasing but not dense
    keyCounter = 1_000_000;
  }

  public static void main(final String[] args) throws Exception {
    final var config = WorkloadConfig.parse(args);
    if (config.fresh()) {
      deleteRecursively(config.dbDir());
    }
    Files.createDirectories(config.dbDir());
    try (final var workload = new JobQueueWorkload(config)) {
      workload.run();
    }
  }

  private void run() throws Exception {
    log("config: %s", config);
    if (config.fresh() && config.preloadKeys() > 0) {
      preload();
    }
    if (config.ageJobs() > 0) {
      age();
    }
    snapshotTickers();

    final var csvExists = Files.exists(config.out());
    try (final var csv =
        new PrintStream(
            Files.newOutputStream(
                config.out(), StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
      if (!csvExists) {
        csv.println(header());
      }
      runMeasured(csv);
    }
  }

  private void runMeasured(final PrintStream csv) throws RocksDBException {
    final long start = System.nanoTime();
    final long end = start + config.duration().toNanos();
    final long windowNanos = config.window().toNanos();
    final long deadlineScanNanos = config.deadlineScanInterval().toNanos();
    long compactAtNanos =
        config.compactAt().isZero() ? Long.MAX_VALUE : start + config.compactAt().toNanos();
    final double jobIntervalNanos = config.jobRate() > 0 ? 1e9 / config.jobRate() : 0;

    long windowStart = start;
    long nextDeadlineScan = start;
    long windowJobs = 0;
    long windowPolls = 0;
    long measuredJobs = 0;
    String phase = "measure";

    long now = start;
    while (now < end) {
      // job lifecycle step, optionally rate limited
      if (jobIntervalNanos == 0 || (now - start) >= measuredJobs * jobIntervalNanos) {
        jobStep();
        measuredJobs++;
        windowJobs++;
      }

      for (int i = 0; i < config.pollsPerJob(); i++) {
        poll();
        windowPolls++;
      }
      for (int i = 0; i < config.getsPerJob(); i++) {
        randomGet();
      }

      now = System.nanoTime();
      if (now >= nextDeadlineScan) {
        scanDeadlines();
        nextDeadlineScan = now + deadlineScanNanos;
      }

      if (now >= compactAtNanos) {
        final long compactionStart = System.nanoTime();
        db.compactRange(handle);
        log("manual compactRange took %d ms", (System.nanoTime() - compactionStart) / 1_000_000);
        compactAtNanos = Long.MAX_VALUE;
        phase = "after-compaction";
        now = System.nanoTime();
      }

      if (now - windowStart >= windowNanos) {
        final double seconds = (now - windowStart) / 1e9;
        csv.println(row((now - start) / 1e9, phase, windowJobs / seconds, windowPolls / seconds));
        csv.flush();
        windowStart = now;
        windowJobs = 0;
        windowPolls = 0;
      }
    }
    log(
        "done: created=%d activated=%d visitedByPolls=%d",
        jobsCreated, jobsActivated, jobsPolledVisited);
  }

  /** Bulk-loads small, unrelated entries to give the DB the size and shape of an aged partition. */
  private void preload() throws RocksDBException {
    final long started = System.nanoTime();
    final var value = new byte[config.preloadValueSize()];
    final var key = ByteBuffer.allocate(Long.BYTES * 2);
    try (final var batch = new WriteBatch()) {
      for (long i = 0; i < config.preloadKeys(); i++) {
        random.nextBytes(value);
        key.clear();
        key.putLong(CF_BACKGROUND_BASE + i % config.preloadPrefixes()).putLong(random.nextLong());
        batch.put(handle, key.array(), value);
        if (config.preloadDeleteRatio() > 0 && random.nextDouble() < config.preloadDeleteRatio()) {
          batch.delete(handle, key.array());
        }
        if (batch.count() >= 10_000) {
          db.write(writeOptions, batch);
          batch.clear();
        }
      }
      db.write(writeOptions, batch);
    }
    try (final var flush = new FlushOptions().setWaitForFlush(true)) {
      db.flush(flush, handle);
    }
    db.compactRange(handle);
    log(
        "preloaded %d keys in %d ms",
        config.preloadKeys(), (System.nanoTime() - started) / 1_000_000);
  }

  /**
   * Runs job lifecycles without polls, as fast as possible, so that the measured phase starts with
   * the tombstones (in memtables and SST files) that a long-lived partition has accumulated.
   */
  private void age() throws RocksDBException {
    if (statistics != null) {
      // only attribute the aging phase's flushes and compactions, not the preload's
      statistics.reset();
    }
    final long started = System.nanoTime();
    for (long i = 0; i < config.ageJobs(); i++) {
      jobStep();
    }
    // the latencies of the aging phase are not part of the measurement
    writeLatency.snapshotAndReset();
    final double seconds = (System.nanoTime() - started) / 1e9;
    log(
        "aged %d jobs in %.1f s (%.0f jobs/s): flushWrite=%.1fMB compactRead=%.1fMB"
            + " compactWrite=%.1fMB stall=%.1fs memtables=%sB L0=%s",
        config.ageJobs(),
        seconds,
        config.ageJobs() / seconds,
        tickerMb(TickerType.FLUSH_WRITE_BYTES),
        tickerMb(TickerType.COMPACT_READ_BYTES),
        tickerMb(TickerType.COMPACT_WRITE_BYTES),
        statistics == null ? 0 : statistics.getTickerCount(TickerType.STALL_MICROS) / 1e6,
        db.getProperty(handle, "rocksdb.cur-size-all-mem-tables"),
        db.getProperty(handle, "rocksdb.num-files-at-level0"));
  }

  private double tickerMb(final TickerType ticker) {
    return statistics == null ? 0 : statistics.getTickerCount(ticker) / 1e6;
  }

  private void jobStep() throws RocksDBException {
    final int churn = nextChurnType++ % churnTypeIndexes.length;
    final byte[] typePrefix = typePrefixes[churnTypeIndexes[churn]];
    keyCounter += config.keysPerJob();
    final long jobKey = Protocol.encodePartitionId(config.partitionId(), keyCounter);

    // create: insert job and make it activatable
    long txStart = System.nanoTime();
    transaction.put(handle, jobKey(jobKey), jobValue);
    transaction.put(handle, activatableKey(typePrefix, jobKey), NIL);
    commit();
    writeLatency.record(System.nanoTime() - txStart);
    activatable[churn].addLong(jobKey);
    final int typeIndex = churnTypeIndexes[churn];
    lowWatermarks[typeIndex] = Math.min(lowWatermarks[typeIndex], jobKey);
    jobsCreated++;

    // activate the oldest job once the backlog is exceeded (push or a non-empty poll)
    if (activatable[churn].size() > config.backlog()) {
      final long activatedKey = activatable[churn].pollLong();
      final long deadline = System.currentTimeMillis() + config.jobTimeout().toMillis();
      txStart = System.nanoTime();
      removeWriteOnce(activatableKey(typePrefix, activatedKey));
      transaction.put(handle, deadlineKey(deadline, activatedKey), NIL);
      transaction.put(handle, jobKey(activatedKey), jobValue);
      commit();
      writeLatency.record(System.nanoTime() - txStart);
      inflightJobs.addLong(activatedKey);
      inflightDeadlines.addLong(deadline);
      jobsActivated++;
    }

    // complete the oldest in-flight job
    if (inflightJobs.size() > config.inflight()) {
      final long completedKey = inflightJobs.pollLong();
      final long deadline = inflightDeadlines.pollLong();
      txStart = System.nanoTime();
      removeWriteOnce(deadlineKey(deadline, completedKey));
      transaction.delete(handle, jobKey(completedKey));
      commit();
      writeLatency.record(System.nanoTime() - txStart);
    }
  }

  /**
   * One empty-or-not JOB_BATCH ACTIVATE poll for a single type, following DbJobState: phase 1 scans
   * the type prefix for priority > 0 jobs, phase 3 seeks to the priority = 0 range of the type. The
   * legacy phase is skipped (drained CF).
   */
  private void poll() throws RocksDBException {
    final int typeIndex = nextPollType++ % typePrefixes.length;
    final byte[] prefix = typePrefixes[typeIndex];
    final byte[] upperBound = typeUpperBounds[typeIndex];
    final long started = System.nanoTime();

    // phase 1: everything in the prefix until the first priority <= 0 entry
    scanPrefix(prefix, prefix, upperBound, true);
    // phase 3: seek to (type, priority 0, jobKey 0, tenant ""), or to the low watermark
    if (config.seekHint()) {
      final byte[] seekKey =
          ByteBuffer.allocate(prefix.length + Integer.BYTES + Long.BYTES + Integer.BYTES)
              .put(prefix)
              .putInt(PRIORITY_ZERO_INVERTED)
              .putLong(lowWatermarks[typeIndex])
              .putInt(0)
              .array();
      final long firstLive = scanPrefix(prefix, seekKey, upperBound, false);
      // no live key below the first one we saw; if none, nothing below the next generated key
      lowWatermarks[typeIndex] =
          firstLive >= 0
              ? firstLive
              : Protocol.encodePartitionId(config.partitionId(), keyCounter + 1);
    } else {
      scanPrefix(prefix, typePriorityZeroSeekKeys[typeIndex], upperBound, false);
    }

    pollLatency.record(System.nanoTime() - started);
  }

  /** Returns the job key of the first live entry visited, or -1 if there was none. */
  private long scanPrefix(
      final byte[] prefix, final byte[] seekKey, final byte[] upperBound, final boolean phaseOne)
      throws RocksDBException {
    long firstLive = -1;
    final long createStart = System.nanoTime();
    Slice upperBoundSlice = null;
    ReadOptions readOptions = prefixReadOptions;
    if (config.readMode() == ReadMode.UPPER_BOUND) {
      // allocated per scan, like a production implementation with dynamic bounds would have to
      upperBoundSlice = new Slice(upperBound);
      readOptions =
          new ReadOptions()
              .setPrefixSameAsStart(true)
              .setTotalOrderSeek(false)
              .setReadaheadSize(0)
              .setIterateUpperBound(upperBoundSlice);
    }
    try (final RocksIterator iterator =
        transaction.newIteratorWithBase(handle, db.newIterator(handle, readOptions))) {
      iteratorCreateLatency.record(System.nanoTime() - createStart);
      int visited = 0;
      for (iterator.seek(seekKey); iterator.isValid(); iterator.next()) {
        final byte[] key = iterator.key();
        if (!startsWith(prefix, key)) {
          break;
        }
        if (phaseOne) {
          // all our jobs have priority 0, so phase 1 stops at the first live entry
          final int invertedPriority = ByteBuffer.wrap(key, prefix.length, Integer.BYTES).getInt();
          if (invertedPriority >= PRIORITY_ZERO_INVERTED) {
            break;
          }
        }
        final long jobKey =
            ByteBuffer.wrap(key, prefix.length + Integer.BYTES, Long.BYTES).getLong();
        if (firstLive < 0) {
          firstLive = jobKey;
        }
        get(jobKey(jobKey));
        jobsPolledVisited++;
        if (++visited >= config.maxJobsToActivate()) {
          break;
        }
      }
      iterator.status();
    } finally {
      if (upperBoundSlice != null) {
        readOptions.close();
        upperBoundSlice.close();
      }
    }
    return firstLive;
  }

  /** Like the job timeout checker: walk deadlines in order until the first one in the future. */
  private void scanDeadlines() throws RocksDBException {
    final long now = System.currentTimeMillis();
    final long started = System.nanoTime();
    Slice upperBoundSlice = null;
    ReadOptions readOptions = prefixReadOptions;
    if (config.readMode() == ReadMode.UPPER_BOUND) {
      upperBoundSlice = new Slice(deadlineScanUpperBound);
      readOptions =
          new ReadOptions()
              .setPrefixSameAsStart(true)
              .setTotalOrderSeek(false)
              .setReadaheadSize(0)
              .setIterateUpperBound(upperBoundSlice);
    }
    try (final RocksIterator iterator =
        transaction.newIteratorWithBase(handle, db.newIterator(handle, readOptions))) {
      // every deadline before the previous scan's timestamp was due then and has been handled
      final byte[] seekKey =
          config.seekHint() && lastDeadlineScan > 0
              ? ByteBuffer.allocate(Long.BYTES * 2)
                  .putLong(CF_DEADLINES)
                  .putLong(lastDeadlineScan)
                  .array()
              : deadlineScanStart;
      for (iterator.seek(seekKey); iterator.isValid(); iterator.next()) {
        final byte[] key = iterator.key();
        if (!startsWith(deadlineScanStart, key)) {
          break;
        }
        final long deadline = ByteBuffer.wrap(key, Long.BYTES, Long.BYTES).getLong();
        if (deadline > now) {
          break;
        }
      }
      iterator.status();
    } finally {
      if (upperBoundSlice != null) {
        readOptions.close();
        upperBoundSlice.close();
      }
    }
    deadlineScanLatency.record(System.nanoTime() - started);
    lastDeadlineScan = now;
  }

  private void randomGet() throws RocksDBException {
    if (inflightJobs.isEmpty()) {
      return;
    }
    // LongArrayQueue has no random access; probing the head is enough as a point-read reference
    final long started = System.nanoTime();
    get(jobKey(inflightJobs.peekLong()));
    getLatency.record(System.nanoTime() - started);
  }

  private void get(final byte[] key) throws RocksDBException {
    transaction.getFromBatchAndDB(db, handle, pointReadOptions, key);
  }

  private void removeWriteOnce(final byte[] key) throws RocksDBException {
    if (config.deleteMode() == DeleteMode.SINGLE_DELETE) {
      transaction.singleDelete(handle, key);
    } else {
      transaction.delete(handle, key);
    }
  }

  private void commit() throws RocksDBException {
    db.write(writeOptions, transaction);
    transaction.clear();
  }

  private static byte[] jobKey(final long jobKey) {
    return ByteBuffer.allocate(Long.BYTES * 2).putLong(CF_JOBS).putLong(jobKey).array();
  }

  private static byte[] deadlineKey(final long deadline, final long jobKey) {
    return ByteBuffer.allocate(Long.BYTES * 3)
        .putLong(CF_DEADLINES)
        .putLong(deadline)
        .putLong(jobKey)
        .array();
  }

  private static byte[] activatableKey(final byte[] typePrefix, final long jobKey) {
    return ByteBuffer.allocate(
            typePrefix.length + Integer.BYTES + Long.BYTES + Integer.BYTES + TENANT.length)
        .put(typePrefix)
        .putInt(PRIORITY_ZERO_INVERTED)
        .putLong(jobKey)
        .putInt(TENANT.length)
        .put(TENANT)
        .array();
  }

  /** Smallest key that is greater than every key starting with {@code prefix}. */
  private static byte[] successor(final byte[] prefix) {
    final byte[] result = prefix.clone();
    for (int i = result.length - 1; i >= 0; i--) {
      if (result[i] != (byte) 0xFF) {
        result[i]++;
        return Arrays.copyOf(result, i + 1);
      }
    }
    throw new IllegalArgumentException("prefix has no successor");
  }

  private static boolean startsWith(final byte[] prefix, final byte[] key) {
    return key.length >= prefix.length
        && Arrays.equals(prefix, 0, prefix.length, key, 0, prefix.length);
  }

  private String header() {
    final var sb =
        new StringBuilder(
            "label,elapsed_s,phase,jobs_per_s,polls_per_s,"
                + "poll_n,poll_mean_us,poll_p50_us,poll_p99_us,poll_p999_us,poll_max_us,"
                + "iter_create_mean_us,iter_create_p99_us,"
                + "get_mean_us,get_p99_us,write_mean_us,write_p99_us,"
                + "deadline_scan_n,deadline_scan_mean_us,deadline_scan_max_us,sst_deletions,sst_entries");
    for (final var property : PROPERTIES) {
      sb.append(',').append(property.substring("rocksdb.".length()));
    }
    for (final var ticker : TICKERS) {
      sb.append(',').append(ticker.name().toLowerCase(Locale.ROOT));
    }
    return sb.toString();
  }

  private String row(
      final double elapsed, final String phase, final double jobsPerSec, final double pollsPerSec)
      throws RocksDBException {
    final var poll = pollLatency.snapshotAndReset();
    final var create = iteratorCreateLatency.snapshotAndReset();
    final var get = getLatency.snapshotAndReset();
    final var write = writeLatency.snapshotAndReset();
    final var deadline = deadlineScanLatency.snapshotAndReset();

    long sstDeletions = 0;
    long sstEntries = 0;
    for (final var props : db.getPropertiesOfAllTables(handle).values()) {
      sstDeletions += props.getNumDeletions();
      sstEntries += props.getNumEntries();
    }

    final var sb = new StringBuilder();
    sb.append(config.label())
        .append(',')
        .append(fmt(elapsed))
        .append(',')
        .append(phase)
        .append(',')
        .append(fmt(jobsPerSec))
        .append(',')
        .append(fmt(pollsPerSec))
        .append(',')
        .append(poll.count())
        .append(',')
        .append(fmt(poll.mean()))
        .append(',')
        .append(fmt(poll.p50()))
        .append(',')
        .append(fmt(poll.p99()))
        .append(',')
        .append(fmt(poll.p999()))
        .append(',')
        .append(fmt(poll.max()))
        .append(',')
        .append(fmt(create.mean()))
        .append(',')
        .append(fmt(create.p99()))
        .append(',')
        .append(fmt(get.mean()))
        .append(',')
        .append(fmt(get.p99()))
        .append(',')
        .append(fmt(write.mean()))
        .append(',')
        .append(fmt(write.p99()))
        .append(',')
        .append(deadline.count())
        .append(',')
        .append(fmt(deadline.mean()))
        .append(',')
        .append(fmt(deadline.max()))
        .append(',')
        .append(sstDeletions)
        .append(',')
        .append(sstEntries);
    for (final var property : PROPERTIES) {
      sb.append(',').append(db.getProperty(handle, property));
    }
    for (final var ticker : TICKERS) {
      final long current = statistics == null ? 0 : statistics.getTickerCount(ticker);
      sb.append(',').append(current - lastTickers.getOrDefault(ticker, 0L));
      lastTickers.put(ticker, current);
    }

    log(
        "t=%6.1fs %-16s jobs/s=%8.0f polls/s=%9.0f poll mean=%8.1fus p99=%8.1fus"
            + " iterCreate=%6.1fus deadlineScan=%8.1fus L0=%s sstDeletes=%d",
        elapsed,
        phase,
        jobsPerSec,
        pollsPerSec,
        poll.mean(),
        poll.p99(),
        create.mean(),
        deadline.mean(),
        db.getProperty(handle, "rocksdb.num-files-at-level0"),
        sstDeletions);
    return sb.toString();
  }

  private void snapshotTickers() {
    if (statistics != null) {
      for (final var ticker : TICKERS) {
        lastTickers.put(ticker, statistics.getTickerCount(ticker));
      }
    }
  }

  @Override
  public void close() throws Exception {
    transaction.close();
    prefixReadOptions.close();
    pointReadOptions.close();
    writeOptions.close();
    zeebeDb.close();
    if (resources instanceof final RocksDbResources.Shared shared) {
      shared.getSharedWriteBufferManager().close();
      shared.getSharedCache().close();
    }
  }

  private static String fmt(final double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static void log(final String format, final Object... args) {
    System.err.printf(Locale.ROOT, format + "%n", args);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(final Class<?> type, final Object target, final String name)
      throws ReflectiveOperationException {
    final Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }

  private static void deleteRecursively(final Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (final Stream<Path> files = Files.walk(path)) {
      files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.rocksdb;

import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.CompactOnDeletion;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/**
 * Parameters of a {@link JobQueueWorkload} run, parsed from {@code key=value} arguments. Keys
 * prefixed with {@code cf.} are passed verbatim as RocksDB column family options, exactly like the
 * broker's {@code rocksdb.columnFamilyOptions} setting.
 */
record WorkloadConfig(
    Path dbDir,
    boolean fresh,
    Path out,
    String label,
    Duration duration,
    Duration window,
    long seed,
    // memory / factory settings
    long memoryLimit,
    MemoryAllocationStrategy memoryStrategy,
    int partitions,
    boolean sstPartitioning,
    boolean statistics,
    Properties cfOptions,
    @Nullable CompactOnDeletion compactOnDeletion,
    // background state
    long preloadKeys,
    int preloadPrefixes,
    int preloadValueSize,
    double preloadDeleteRatio,
    // job workload
    int partitionId,
    int keysPerJob,
    long ageJobs,
    int pollTypes,
    int churnTypes,
    int backlog,
    int inflight,
    long jobRate,
    int pollsPerJob,
    int getsPerJob,
    int maxJobsToActivate,
    int jobValueSize,
    Duration jobTimeout,
    Duration deadlineScanInterval,
    // variants
    ReadMode readMode,
    DeleteMode deleteMode,
    boolean seekHint,
    Duration compactAt) {

  /** How prefix iterators are configured. */
  enum ReadMode {
    /** Exactly what TransactionalColumnFamily does today: prefix_same_as_start, no bounds. */
    ZEEBE,
    /** Same as ZEEBE, plus iterate_upper_bound set to the successor of the seek prefix. */
    UPPER_BOUND
  }

  /** How write-once index entries (activatable jobs, deadlines) are removed. */
  enum DeleteMode {
    DELETE,
    SINGLE_DELETE
  }

  static WorkloadConfig parse(final String[] args) {
    final Map<String, String> kv = new HashMap<>();
    final var cfOptions = new Properties();
    for (final var arg : args) {
      final var idx = arg.indexOf('=');
      if (idx <= 0) {
        throw new IllegalArgumentException("Expected key=value argument, got: " + arg);
      }
      final var key = arg.substring(0, idx);
      final var value = arg.substring(idx + 1);
      if (key.startsWith("cf.")) {
        cfOptions.setProperty(key.substring(3), value);
      } else {
        kv.put(key, value);
      }
    }

    final var params = new Params(kv);
    final var config =
        new WorkloadConfig(
            Path.of(params.str("dbDir", "/tmp/zeebe-rocksdb-bench/db")),
            params.bool("fresh", true),
            Path.of(params.str("out", "results.csv")),
            params.str("label", "default"),
            params.duration("duration", "PT120S"),
            params.duration("window", "PT5S"),
            params.lng("seed", 42),
            params.bytes("memoryLimit", "512MB"),
            MemoryAllocationStrategy.valueOf(params.str("memoryStrategy", "PARTITION")),
            params.integer("partitions", 1),
            params.bool("sstPartitioning", true),
            params.bool("statistics", true),
            cfOptions,
            params.compactOnDeletion("compactOnDeletion"),
            params.lng("preloadKeys", 4_000_000),
            params.integer("preloadPrefixes", 8),
            params.integer("preloadValueSize", 24),
            params.dbl("preloadDeleteRatio", 0.0),
            params.integer("partitionId", 3),
            params.integer("keysPerJob", 8),
            params.lng("ageJobs", 0),
            params.integer("pollTypes", 20),
            params.integer("churnTypes", 2),
            params.integer("backlog", 0),
            params.integer("inflight", 1000),
            params.lng("jobRate", 0),
            params.integer("pollsPerJob", 10),
            params.integer("getsPerJob", 2),
            params.integer("maxJobsToActivate", 32),
            params.integer("jobValueSize", 400),
            params.duration("jobTimeout", "PT5M"),
            params.duration("deadlineScanInterval", "PT1S"),
            ReadMode.valueOf(params.str("readMode", "ZEEBE")),
            DeleteMode.valueOf(params.str("deleteMode", "DELETE")),
            params.bool("seekHint", false),
            params.duration("compactAt", "PT0S"));
    params.assertAllConsumed();
    return config;
  }

  private static final class Params {
    private final Map<String, String> values;

    private Params(final Map<String, String> values) {
      this.values = new HashMap<>(values);
    }

    String str(final String key, final String defaultValue) {
      final var value = values.remove(key);
      return value == null ? defaultValue : value;
    }

    boolean bool(final String key, final boolean defaultValue) {
      return Boolean.parseBoolean(str(key, Boolean.toString(defaultValue)));
    }

    int integer(final String key, final int defaultValue) {
      return Integer.parseInt(str(key, Integer.toString(defaultValue)));
    }

    long lng(final String key, final long defaultValue) {
      return Long.parseLong(str(key, Long.toString(defaultValue)).replace("_", ""));
    }

    double dbl(final String key, final double defaultValue) {
      return Double.parseDouble(str(key, Double.toString(defaultValue)));
    }

    Duration duration(final String key, final String defaultValue) {
      return Duration.parse(str(key, defaultValue));
    }

    long bytes(final String key, final String defaultValue) {
      final var raw = str(key, defaultValue).toUpperCase();
      final long multiplier;
      final String number;
      if (raw.endsWith("GB")) {
        multiplier = 1L << 30;
        number = raw.substring(0, raw.length() - 2);
      } else if (raw.endsWith("MB")) {
        multiplier = 1L << 20;
        number = raw.substring(0, raw.length() - 2);
      } else if (raw.endsWith("KB")) {
        multiplier = 1L << 10;
        number = raw.substring(0, raw.length() - 2);
      } else {
        multiplier = 1;
        number = raw;
      }
      return Long.parseLong(number.trim()) * multiplier;
    }

    /** Format: {@code windowSize:deletionTrigger:deletionRatio}, e.g. {@code 1000:500:0.5}. */
    @Nullable CompactOnDeletion compactOnDeletion(final String key) {
      final var raw = values.remove(key);
      if (raw == null) {
        return null;
      }
      final var parts = raw.split(":");
      return new CompactOnDeletion(
          Long.parseLong(parts[0]), Long.parseLong(parts[1]), Double.parseDouble(parts[2]));
    }

    void assertAllConsumed() {
      if (!values.isEmpty()) {
        throw new IllegalArgumentException("Unknown arguments: " + values.keySet());
      }
    }
  }
}

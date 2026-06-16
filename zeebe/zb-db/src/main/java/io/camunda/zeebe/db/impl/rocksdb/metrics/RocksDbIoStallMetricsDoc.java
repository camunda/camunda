/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.metrics;

import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/**
 * Documents the write-stall counters extracted from the RocksDB {@code rocksdb.cfstats} map
 * property.
 *
 * <p>Unlike {@link RocksDbMetricsDoc}, these are not standalone integer properties: each one is a
 * single entry within the map returned by {@code DB::GetMapProperty("rocksdb.cfstats")}. The value
 * is a cumulative count, since the database was opened, of how many times writes were stopped
 * (fully halted) or slowed down (throttled) for the given reason.
 *
 * <p>These counters come from RocksDB's always-on {@code InternalStats}, so reading them does not
 * require enabling the (more expensive) RocksDB {@code Statistics} object.
 *
 * <p>Each value is a cumulative count and is exported as a counter (see {@link #getType()}). It
 * resets to zero when the database is reopened, which Prometheus {@code rate()} handles as a
 * counter reset.
 */
public enum RocksDbIoStallMetricsDoc implements RocksDbMeterDoc {
  TOTAL_STOP(
      "total-stops",
      "io.stalls.stop",
      "Cumulative number of times writes were fully stopped for any reason"),
  TOTAL_SLOWDOWN(
      "total-delays",
      "io.stalls.slowdown",
      "Cumulative number of times writes were slowed down for any reason"),
  MEMTABLE_STOP(
      "memtable-limit-stops",
      "io.stalls.memtable.stop",
      "Cumulative number of write stops caused by too many unflushed memtables"),
  MEMTABLE_SLOWDOWN(
      "memtable-limit-delays",
      "io.stalls.memtable.slowdown",
      "Cumulative number of write slowdowns caused by approaching the memtable limit"),
  L0_FILE_COUNT_STOP(
      "l0-file-count-limit-stops",
      "io.stalls.l0.file.count.stop",
      "Cumulative number of write stops caused by hitting the L0 file-count limit"),
  L0_FILE_COUNT_SLOWDOWN(
      "l0-file-count-limit-delays",
      "io.stalls.l0.file.count.slowdown",
      "Cumulative number of write slowdowns caused by approaching the L0 file-count limit"),
  PENDING_COMPACTION_STOP(
      "pending-compaction-bytes-stops",
      "io.stalls.pending.compaction.bytes.stop",
      "Cumulative number of write stops caused by exceeding the hard pending-compaction-bytes limit"),
  PENDING_COMPACTION_SLOWDOWN(
      "pending-compaction-bytes-delays",
      "io.stalls.pending.compaction.bytes.slowdown",
      "Cumulative number of write slowdowns caused by exceeding the soft pending-compaction-bytes limit");

  private static final String ZEEBE_NAMESPACE = "zeebe";
  private static final String IO_STALLS_NAMESPACE = "rocksdb.writes";

  private final String mapKey;
  private final String suffix;
  private final String description;

  RocksDbIoStallMetricsDoc(final String mapKey, final String suffix, final String description) {
    this.mapKey = mapKey;
    this.suffix = suffix;
    this.description = description;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getName() {
    return ZEEBE_NAMESPACE + "." + IO_STALLS_NAMESPACE + "." + suffix;
  }

  @Override
  public Type getType() {
    return Type.COUNTER;
  }

  @Override
  public KeyName[] getAdditionalKeyNames() {
    return PartitionKeyNames.values();
  }

  @Override
  public String namespace() {
    return IO_STALLS_NAMESPACE;
  }

  /** The key under which this counter appears in the {@code rocksdb.cfstats} map. */
  @Override
  public String propertyName() {
    return mapKey;
  }
}

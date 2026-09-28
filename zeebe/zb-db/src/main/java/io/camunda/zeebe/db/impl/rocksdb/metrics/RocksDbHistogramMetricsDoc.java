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
import io.micrometer.core.instrument.binder.BaseUnits;
import org.rocksdb.HistogramType;

/**
 * Documents the RocksDB histograms exported to Micrometer. Like {@link RocksDbTickerMetricsDoc},
 * these require an attached RocksDB {@code Statistics} object and are absent otherwise.
 *
 * <p>These measure time spent <em>inside</em> RocksDB, which is what distinguishes a read that was
 * served from the block cache from one that went to the filesystem. The equivalent Zeebe-side
 * measurement, {@code zeebe.rocksdb.latency}, includes the JNI boundary and Zeebe's own
 * serialization, so the two are complementary rather than redundant.
 *
 * <p>Each histogram is exported as three gauges — {@code .count}, {@code .sum} and {@code .p99}.
 * RocksDB accumulates its histograms since the database was opened and we never reset them, so
 * {@code .p99} flattens out over the lifetime of a partition and is only meaningful early on or as
 * a coarse ceiling. The useful signal is {@code rate(sum) / rate(count)}, which gives the mean
 * within the query window. Durations are exported in milliseconds.
 */
public enum RocksDbHistogramMetricsDoc implements RocksDbMeterDoc {
  DB_GET(
      HistogramType.DB_GET,
      "db.get",
      "Time spent inside RocksDB serving a point get, in milliseconds",
      BaseUnits.MILLISECONDS,
      1.0 / 1000.0),
  DB_SEEK(
      HistogramType.DB_SEEK,
      "db.seek",
      "Time spent inside RocksDB serving an iterator seek, in milliseconds",
      BaseUnits.MILLISECONDS,
      1.0 / 1000.0),
  DB_WRITE(
      HistogramType.DB_WRITE,
      "db.write",
      "Time spent inside RocksDB applying a write batch, in milliseconds",
      BaseUnits.MILLISECONDS,
      1.0 / 1000.0),
  SST_READ_MICROS(
      HistogramType.SST_READ_MICROS,
      "sst.read",
      "Time spent in a single SST file read, in milliseconds. A rising mean here means reads are going to the filesystem rather than the block cache",
      BaseUnits.MILLISECONDS,
      1.0 / 1000.0),
  READ_BLOCK_GET_MICROS(
      HistogramType.READ_BLOCK_GET_MICROS,
      "read.block.get",
      "Time spent fetching a block on behalf of a get, in milliseconds",
      BaseUnits.MILLISECONDS,
      1.0 / 1000.0),
  BYTES_PER_READ(
      HistogramType.BYTES_PER_READ,
      "bytes.per.read",
      "Distribution of bytes returned per read",
      BaseUnits.BYTES);

  /** The gauge suffixes each histogram is expanded into. */
  public enum Statistic {
    COUNT("count", "number of recorded samples"),
    SUM("sum", "sum of all recorded samples"),
    P99("p99", "99th percentile over the lifetime of the database");

    private final String suffix;
    private final String description;

    Statistic(final String suffix, final String description) {
      this.suffix = suffix;
      this.description = description;
    }

    public String suffix() {
      return suffix;
    }

    public String description() {
      return description;
    }
  }

  private static final String ZEEBE_NAMESPACE = "zeebe";
  private static final String HISTOGRAM_NAMESPACE = "rocksdb.histogram";

  private final HistogramType histogram;
  private final String suffix;
  private final String description;
  private final String baseUnit;
  private final double scale;

  RocksDbHistogramMetricsDoc(
      final HistogramType histogram,
      final String suffix,
      final String description,
      final String baseUnit) {
    this(histogram, suffix, description, baseUnit, 1.0);
  }

  RocksDbHistogramMetricsDoc(
      final HistogramType histogram,
      final String suffix,
      final String description,
      final String baseUnit,
      final double scale) {
    this.histogram = histogram;
    this.suffix = suffix;
    this.description = description;
    this.baseUnit = baseUnit;
    this.scale = scale;
  }

  public HistogramType histogram() {
    return histogram;
  }

  /** The full meter name for one of the three statistics this histogram is expanded into. */
  public String nameFor(final Statistic statistic) {
    return getName() + "." + statistic.suffix();
  }

  public String descriptionFor(final Statistic statistic) {
    return description + " — " + statistic.description();
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getName() {
    return ZEEBE_NAMESPACE + "." + HISTOGRAM_NAMESPACE + "." + suffix;
  }

  @Override
  public String getBaseUnit() {
    return baseUnit;
  }

  @Override
  public double convertToBaseUnit(final double value) {
    return value * scale;
  }

  @Override
  public Type getType() {
    return Type.GAUGE;
  }

  @Override
  public KeyName[] getAdditionalKeyNames() {
    return PartitionKeyNames.values();
  }

  /** The name of the underlying RocksDB histogram. */
  @Override
  public String propertyName() {
    return histogram.name();
  }

  @Override
  public String namespace() {
    return HISTOGRAM_NAMESPACE;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.DEFAULT_ROCKSDB_MEMORY_ALLOCATION_STRATEGY;

import com.sun.management.OperatingSystemMXBean;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.lang.management.ManagementFactory;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

public final class RocksdbCfg implements ConfigurationEntry {
  public static final long MINIMUM_PARTITION_MEMORY_LIMIT = 32 * 1024 * 1024L;
  public static final double ADVICE_MAX_MEMORY_FRACTION = 0.5;
  private static final Logger LOGGER = LoggerFactory.getLogger(RocksdbCfg.class);

  private Properties columnFamilyOptions;
  private boolean enableStatistics = RocksDbConfiguration.DEFAULT_STATISTICS_ENABLED;
  private AccessMetricsConfiguration.Kind accessMetrics = AccessMetricsConfiguration.Kind.NONE;
  private DataSize memoryLimit = DataSize.ofBytes(RocksDbConfiguration.DEFAULT_MEMORY_LIMIT);
  private int maxOpenFiles = RocksDbConfiguration.DEFAULT_UNLIMITED_MAX_OPEN_FILES;
  private int maxWriteBufferNumber = RocksDbConfiguration.DEFAULT_MAX_WRITE_BUFFER_NUMBER;
  private int minWriteBufferNumberToMerge =
      RocksDbConfiguration.DEFAULT_MIN_WRITE_BUFFER_NUMBER_TO_MERGE;
  private int ioRateBytesPerSecond = RocksDbConfiguration.DEFAULT_IO_RATE_BYTES_PER_SECOND;
  private boolean disableWal = RocksDbConfiguration.DEFAULT_WAL_DISABLED;
  private boolean enableSstPartitioning = RocksDbConfiguration.DEFAULT_SST_PARTITIONING_ENABLED;
  private MemoryAllocationStrategy memoryAllocationStrategy =
      DEFAULT_ROCKSDB_MEMORY_ALLOCATION_STRATEGY;
  private double memoryFraction = 0.1;
  private double maxMemoryFraction = -1;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    if (columnFamilyOptions == null) {
      // lazy init to not have to deal with null when using these options
      columnFamilyOptions = new Properties();
    } else {
      // Since (some of) the columnFamilyOptions may have been provided as environment variables,
      // we must do some transformations on the entries of this properties object.
      columnFamilyOptions = initColumnFamilyOptions(columnFamilyOptions);
    }
  }

  long getMemoryLimitBytes(final int partitionsPerBrokerCount) {

    return switch (getMemoryAllocationStrategy()) {
      case BROKER -> getMemoryLimit().toBytes();
      case FRACTION -> getFixedMemoryPercentage(getMemoryFraction());
      // in case of PARTITION or null, we allocate per partition
      default -> getMemoryLimit().toBytes() * partitionsPerBrokerCount;
    };
  }

  private static long getFixedMemoryPercentage(final double memoryFraction) {
    // get total memory from the OS bean.
    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
    return Math.round(totalMemorySize * memoryFraction);
  }

  public void validateRocksDbMemory(final int partitionsPerBrokerCount) {
    final long blockCacheBytes = getMemoryLimitBytes(partitionsPerBrokerCount);

    final long totalMemorySize =
        ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();

    if (getMemoryAllocationStrategy() == MemoryAllocationStrategy.FRACTION) {
      // check that memoryFraction is between 0 and 1 and warn if it is too high
      warnIfTooHighFraction();
    } else {
      // for strategies other than FRACTION which are static sizes, we check if maxMemoryFraction is
      // correctly set, and if so, we validate the allocated memory does not go above the threshold.
      validateMaxMemoryFraction(totalMemorySize, blockCacheBytes);
      // validate that the allocated memory does not exceed total system memory.
      validateMemoryDoesNotExceedSystemMemory(
          totalMemorySize, blockCacheBytes, partitionsPerBrokerCount);
    }

    // validate that each partition has at least the minimum required memory
    if (blockCacheBytes / partitionsPerBrokerCount < MINIMUM_PARTITION_MEMORY_LIMIT) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB per partition to be at least %s bytes, but was %s bytes.",
              MINIMUM_PARTITION_MEMORY_LIMIT, blockCacheBytes / partitionsPerBrokerCount));
    }
  }

  private void validateMaxMemoryFraction(final long totalMemorySize, final long blockCacheBytes) {
    final double maxMemoryFraction = getMaxMemoryFraction();
    if (maxMemoryFraction == -1) {
      LOGGER.debug(
          "Max Memory check for RocksDB is disabled. This can be configured setting CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_MAXMEMORYFRACTION with a value between 0 and 1 to set the max fraction that RocksDB can take of total RAM memory.");
      return;
    }

    if (maxMemoryFraction <= 0 || maxMemoryFraction > 1.0) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the maxMemoryFraction for RocksDB to be between 0 and 1 (exclusive of 0) or -1 (disabled), but was %s.",
              maxMemoryFraction));
    }

    final long maxRocksDbMem = (long) (totalMemorySize * maxMemoryFraction);

    if (blockCacheBytes > maxRocksDbMem
        && getMemoryAllocationStrategy() != MemoryAllocationStrategy.FRACTION) {
      // this check does not apply for FRACTION strategy as it is calculated
      // based on available memory
      throw new IllegalArgumentException(
          String.format(
              "Expected the allocated memory for RocksDB to be below or "
                  + "equal %.2f %% of ram memory, but was %.2f %%.",
              maxMemoryFraction * 100, ((double) blockCacheBytes / totalMemorySize * 100)));
    }
  }

  void validateMemoryDoesNotExceedSystemMemory(
      final long totalMemorySize, final long blockCacheBytes, final int partitionsCount) {
    if (blockCacheBytes > totalMemorySize) {
      final String configHint =
          switch (memoryAllocationStrategy) {
            case BROKER, PARTITION ->
                "Consider reducing the value of CAMUNDA_DATA_PRIMARYSTORAGE_ROCKSDB_MEMORYLIMIT.";
            case FRACTION ->
                throw new IllegalStateException(
                    "Unexpected value: FRACTION should be within [0,1]");
          };
      throw new IllegalArgumentException(
          String.format(
              "Requested RocksDB memory (%d bytes / %d MB) exceeds total system memory (%d bytes / %d MB). "
                  + "Memory allocation strategy: %s. Partitions count: %d. %s",
              blockCacheBytes,
              blockCacheBytes / (1024 * 1024),
              totalMemorySize,
              totalMemorySize / (1024 * 1024),
              getMemoryAllocationStrategy(),
              partitionsCount,
              configHint));
    }
  }

  private void warnIfTooHighFraction() {
    if (memoryFraction <= 0.0 || memoryFraction >= 1.0) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the memoryFraction for RocksDB FRACTION memory allocation strategy to be between 0 and 1, but was %s.",
              memoryFraction));
    }

    if (memoryFraction >= ADVICE_MAX_MEMORY_FRACTION) {
      LOGGER.warn(
          "The configured memoryFraction for RocksDB is set to {}, which is quite high and may lead to out of memory issues for the broker.",
          memoryFraction);
    }
  }

  private static Properties initColumnFamilyOptions(final Properties original) {
    final var result = new Properties();
    original.entrySet().stream()
        .map(RocksDBColumnFamilyOption::new)
        .forEach(entry -> result.put(entry.key, entry.value));
    return result;
  }

  public Properties getColumnFamilyOptions() {
    return columnFamilyOptions;
  }

  public void setColumnFamilyOptions(final Properties columnFamilyOptions) {
    this.columnFamilyOptions = columnFamilyOptions;
  }

  public boolean isEnableStatistics() {
    return enableStatistics;
  }

  public void setEnableStatistics(final boolean enableStatistics) {
    this.enableStatistics = enableStatistics;
  }

  public DataSize getMemoryLimit() {
    return memoryLimit;
  }

  public void setMemoryLimit(final DataSize memoryLimit) {
    this.memoryLimit = memoryLimit;
  }

  public double getMemoryFraction() {
    return memoryFraction;
  }

  public void setMemoryFraction(final double memoryFraction) {
    this.memoryFraction = memoryFraction;
  }

  public MemoryAllocationStrategy getMemoryAllocationStrategy() {
    return memoryAllocationStrategy;
  }

  public void setMemoryAllocationStrategy(final MemoryAllocationStrategy memoryAllocationStrategy) {
    this.memoryAllocationStrategy = memoryAllocationStrategy;
  }

  public double getMaxMemoryFraction() {
    return maxMemoryFraction;
  }

  public void setMaxMemoryFraction(final double maxMemoryFraction) {
    this.maxMemoryFraction = maxMemoryFraction;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public void setMaxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
  }

  public int getMaxWriteBufferNumber() {
    return maxWriteBufferNumber;
  }

  public void setMaxWriteBufferNumber(final int maxWriteBufferNumber) {
    this.maxWriteBufferNumber = maxWriteBufferNumber;
  }

  public int getMinWriteBufferNumberToMerge() {
    return minWriteBufferNumberToMerge;
  }

  public void setMinWriteBufferNumberToMerge(final int minWriteBufferNumberToMerge) {
    this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
  }

  public int getIoRateBytesPerSecond() {
    return ioRateBytesPerSecond;
  }

  public void setIoRateBytesPerSecond(final int ioRateBytesPerSecond) {
    this.ioRateBytesPerSecond = ioRateBytesPerSecond;
  }

  public boolean isDisableWal() {
    return disableWal;
  }

  public void setDisableWal(final boolean disableWal) {
    this.disableWal = disableWal;
  }

  public boolean isEnableSstPartitioning() {
    return enableSstPartitioning;
  }

  public void setEnableSstPartitioning(final boolean enableSstPartitioning) {
    this.enableSstPartitioning = enableSstPartitioning;
  }

  public AccessMetricsConfiguration.Kind getAccessMetrics() {
    return accessMetrics;
  }

  public void setAccessMetrics(final AccessMetricsConfiguration.Kind accessMetrics) {
    this.accessMetrics = accessMetrics;
  }

  public RocksDbConfiguration createRocksDbConfiguration() {
    return new RocksDbConfiguration()
        .setColumnFamilyOptions(columnFamilyOptions)
        .setMaxOpenFiles(maxOpenFiles)
        .setMaxWriteBufferNumber(maxWriteBufferNumber)
        .setMemoryLimit(memoryLimit.toBytes())
        .setMinWriteBufferNumberToMerge(minWriteBufferNumberToMerge)
        .setStatisticsEnabled(enableStatistics)
        .setIoRateBytesPerSecond(ioRateBytesPerSecond)
        .setWalDisabled(disableWal)
        .setSstPartitioningEnabled(enableSstPartitioning)
        .setMemoryAllocationStrategy(memoryAllocationStrategy)
        .setMemoryFraction(memoryFraction);
  }

  @Override
  public String toString() {
    return "RocksdbCfg{"
        + "columnFamilyOptions="
        + columnFamilyOptions
        + ", enableStatistics="
        + enableStatistics
        + ", accessMetrics="
        + accessMetrics
        + ", memoryLimit="
        + memoryLimit
        + ", memoryAllocationStrategy="
        + memoryAllocationStrategy
        + ", memoryFraction="
        + memoryFraction
        + ", maxMemoryFraction="
        + maxMemoryFraction
        + ", maxOpenFiles="
        + maxOpenFiles
        + ", maxWriteBufferNumber="
        + maxWriteBufferNumber
        + ", minWriteBufferNumberToMerge="
        + minWriteBufferNumberToMerge
        + ", ioRateBytesPerSecond="
        + ioRateBytesPerSecond
        + ", disableWal="
        + disableWal
        + ", enableSstPartitioning="
        + enableSstPartitioning
        + '}';
  }

  private static final class RocksDBColumnFamilyOption {

    private static final Pattern DOT_CHAR_PATTERN = Pattern.compile("\\.");
    private static final String UNDERSCORE_CHAR = "_";
    private final String key;
    private final Object value;

    private RocksDBColumnFamilyOption(final Entry<Object, Object> entry) {
      Objects.requireNonNull(entry.getKey());
      // The key of the entry may contain dot chars when provided as an Environment Variable.
      // These should always be replaced with underscores to match the available property names.
      // For example:
      // `ZEEBE_BROKER_EXPERIMENTAL_ROCKSDB_COLUMNFAMILYOPTIONS_WRITE_BUFFER_SIZE=8388608`
      // would result in a key with name `write.buffer.size`, but should be `write_buffer_size`.
      key = replaceAllDotCharsWithUnderscore(entry.getKey().toString());
      value = entry.getValue(); // the value can stay the same
    }

    private static String replaceAllDotCharsWithUnderscore(final String key) {
      return DOT_CHAR_PATTERN.matcher(key).replaceAll(UNDERSCORE_CHAR);
    }
  }
}

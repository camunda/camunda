/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.metrics;

import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum RocksDbMetricsDoc implements RocksDbMeterDoc {
  CUR_SIZE_ALL_MEM_TABLES {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.cur-size-all-mem-tables";
    }
  },

  CUR_SIZE_ACTIVE_MEM_TABLE {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.cur-size-active-mem-table";
    }
  },

  SIZE_ALL_MEM_TABLES {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.size-all-mem-tables";
    }
  },

  BLOCK_CACHE_USAGE {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.block-cache-usage";
    }
  },

  BLOCK_CACHE_CAPACITY {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.block-cache-capacity";
    }
  },

  BLOCK_CACHE_PINNED_USAGE {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.block-cache-pinned-usage";
    }
  },

  ESTIMATE_TABLE_READERS_MEM {
    @Override
    public String getDescription() {
      return MEMORY_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return MEMORY_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.estimate-table-readers-mem";
    }
  },

  TOTAL_SST_FILES_SIZE {
    @Override
    public String getDescription() {
      return SST_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return SST_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.total-sst-files-size";
    }
  },

  LIVE_SST_FILES_SIZE {
    @Override
    public String getDescription() {
      return SST_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return SST_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.live-sst-files-size";
    }
  },

  NUM_ENTRIES_IMM_MEM_TABLES {
    @Override
    public String getDescription() {
      return LIVE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return LIVE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.num-entries-imm-mem-tables";
    }
  },

  ESTIMATE_NUM_KEYS {
    @Override
    public String getDescription() {
      return LIVE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return LIVE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.estimate-num-keys";
    }
  },

  ESTIMATE_LIVE_DATA_SIZE {
    @Override
    public String getDescription() {
      return LIVE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return LIVE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.estimate-live-data-size";
    }
  },

  IS_WRITE_STOPPED {
    @Override
    public String getDescription() {
      return WRITE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return WRITE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.is-write-stopped";
    }
  },

  ACTUAL_DELAYED_WRITE_RATE {
    @Override
    public String getDescription() {
      return WRITE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return WRITE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.actual-delayed-write-rate";
    }
  },

  MEM_TABLE_FLUSH_PENDING {
    @Override
    public String getDescription() {
      return WRITE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return WRITE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.mem-table-flush-pending";
    }
  },

  NUM_RUNNING_FLUSHES {
    @Override
    public String getDescription() {
      return WRITE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return WRITE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.num-running-flushes";
    }
  },

  NUM_RUNNING_COMPACTIONS {
    @Override
    public String getDescription() {
      return WRITE_METRICS_HELP;
    }

    @Override
    public String namespace() {
      return WRITE_METRICS_PREFIX;
    }

    @Override
    public String propertyName() {
      return "rocksdb.num-running-compactions";
    }
  };

  private static final String ZEEBE_NAMESPACE = "zeebe";
  private static final String MEMORY_METRICS_HELP =
      "Everything which might be related to current memory consumption of RocksDB per column family and partition";
  private static final String MEMORY_METRICS_PREFIX = "rocksdb.memory";
  private static final String SST_METRICS_HELP =
      "Everything which is related to SST files in RocksDB per column family and partition";
  private static final String SST_METRICS_PREFIX = "rocksdb.sst";
  private static final String LIVE_METRICS_HELP =
      "Other estimated properties based on entries in RocksDb per column family and partition";
  private static final String LIVE_METRICS_PREFIX = "rocksdb.live";
  private static final String WRITE_METRICS_HELP =
      "Properties related to writes, flushes and compactions for RocksDb per column family and partition";
  private static final String WRITE_METRICS_PREFIX = "rocksdb.writes";

  protected String gaugeSuffix() {
    final var propertyName = propertyName();

    // cut off "rocksdb." prefix
    final String suffix = propertyName.substring(propertyName.indexOf(".") + 1);
    return suffix.replaceAll("-", ".");
  }

  @Override
  public String getName() {
    return ZEEBE_NAMESPACE + "." + namespace() + "." + gaugeSuffix();
  }

  @Override
  public Type getType() {
    return Type.GAUGE;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/** Metrics of the write-behind cache, per partition. */
@SuppressWarnings("NullableProblems")
public enum WriteBehindMetricsDoc implements ExtendedMeterDocumentation {
  /** Point reads that went to the read cache */
  CACHE_GETS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.cache.gets";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Point reads that went to the read cache";
    }
  },

  /** Point reads the read cache didn't hold, so it loaded them from the backend */
  CACHE_LOADS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.cache.loads";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Point reads the read cache didn't hold, so it loaded them from the backend. Divided"
          + " by the cache gets, it's the miss rate";
    }
  },

  /** Entries the read cache evicted to stay within its size */
  CACHE_EVICTIONS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.cache.evictions";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Entries the read cache evicted to stay within its size";
    }
  },

  /** Estimated bytes held by the read cache */
  CACHE_SIZE {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.cache.size";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getBaseUnit() {
      return "bytes";
    }

    @Override
    public String getDescription() {
      return "Estimated bytes held by the read cache";
    }
  },

  /** Point reads answered by versions the backend doesn't have yet */
  UNFLUSHED_READS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.unflushed.reads";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Point reads answered by versions the backend doesn't have yet";
    }
  },

  /** Lock-free point reads that a concurrent commit or cleanup invalidated */
  READ_RETRIES {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.read.retries";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Lock-free point reads that a concurrent commit or cleanup invalidated";
    }
  },

  /** Point reads that took the read lock after their lock-free attempts were invalidated */
  LOCKED_READS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.read.locked";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Point reads that took the read lock after their lock-free attempts were invalidated";
    }
  },

  /** Keys written by committed transactions */
  COMMITTED_KEYS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.committed.keys";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Keys written by committed transactions. Compared to the written keys, it shows how"
          + " many writes the writer combined";
    }
  },

  /** Time a commit waited for another one to finish */
  COMMIT_LOCK_WAIT {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.commit.lock.wait";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time a commit waited for another one to finish";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return MICROS_BUCKETS;
    }
  },

  /** Time a commit waited for the writer, because the pending bytes were above their limit */
  BACKPRESSURE_WAIT {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.commit.backpressure.wait";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time a commit waited for the writer, because the pending bytes were above their"
          + " limit. Only commits that waited are recorded";
    }
  },

  /** Keys with a version the backend doesn't have yet */
  UNFLUSHED_KEYS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.unflushed.keys";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Keys with a version the backend doesn't have yet";
    }
  },

  /** Committed transactions the backend doesn't have yet */
  PENDING_TRANSACTIONS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.pending.transactions";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Committed transactions the backend doesn't have yet";
    }
  },

  /** Key and value bytes of the committed transactions the backend doesn't have yet */
  PENDING_BYTES {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.pending.size";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getBaseUnit() {
      return "bytes";
    }

    @Override
    public String getDescription() {
      return "Key and value bytes of the committed transactions the backend doesn't have yet";
    }
  },

  /** Keys the writer wrote to the backend, by operation */
  WRITTEN_KEYS {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.written.keys";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Keys the writer wrote to the backend, by operation (upsert or delete)";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {WriteBehindKeyNames.OPERATION};
    }
  },

  /** Committed transactions per backend transaction */
  WRITE_TRANSACTIONS {
    private static final double[] BUCKETS = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000};

    @Override
    public String getName() {
      return "zeebe.db.write.behind.write.transactions";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public String getDescription() {
      return "Committed transactions per backend transaction";
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }
  },

  /** Distinct keys per backend transaction */
  WRITE_KEYS {
    private static final double[] BUCKETS = {1, 10, 50, 100, 250, 500, 1000, 2500, 5000, 10000};

    @Override
    public String getName() {
      return "zeebe.db.write.behind.write.keys";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public String getDescription() {
      return "Distinct keys per backend transaction";
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }
  },

  /** Time to write one backend transaction, including retries */
  WRITE_LATENCY {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.write.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time to write one backend transaction, including retries";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return MICROS_BUCKETS;
    }
  },

  /** Backend transactions the writer retried after a recoverable error */
  WRITE_RETRIES {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.write.retries";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Backend transactions the writer retried after a recoverable error";
    }
  },

  /** Times the cache failed, after which commits and snapshots fail */
  FAILURES {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.failures";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Times the cache failed, after which commits and snapshots fail";
    }
  },

  /** Time a scan held publications back, until the backend fixed its view */
  SCAN_HOLD {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.scan.hold";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time a scan held commits and cleanup back, from copying the unflushed versions until"
          + " the backend's first callback";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return MICROS_BUCKETS;
    }
  },

  /** Time a snapshot waited for the writer to write everything committed before it */
  SNAPSHOT_CATCH_UP {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.snapshot.catch.up";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time a snapshot waited for the writer to write everything committed before it";
    }
  },

  /** Time the backend took to take a snapshot, after the writer caught up */
  SNAPSHOT_CHECKPOINT {
    @Override
    public String getName() {
      return "zeebe.db.write.behind.snapshot.checkpoint";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time the backend took to take a snapshot, after the writer caught up";
    }
  };

  private static final Duration[] MICROS_BUCKETS =
      MicrometerUtil.exponentialBucketDuration(10, 2, 15, ChronoUnit.MICROS);

  @Override
  public KeyName[] getAdditionalKeyNames() {
    return PartitionKeyNames.values();
  }

  @SuppressWarnings("NullableProblems")
  public enum WriteBehindKeyNames implements KeyName {
    /** Whether the writer upserted or deleted the key: {@code upsert} or {@code delete} */
    OPERATION {
      @Override
      public String asString() {
        return "operation";
      }
    }
  }
}

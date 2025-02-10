/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@SuppressWarnings("NullableProblems")
public enum JournalMetricsDoc implements ExtendedMeterDocumentation {
  /** Time spend to create a new segment */
  SEGMENT_CREATION_TIME {
    @Override
    public String getName() {
      return "atomix.segment.creation.time";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spend to create a new segment";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return super.getTimerSLOs();
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Time spend to truncate a segment */
  SEGMENT_TRUNCATE_TIME {
    @Override
    public String getName() {
      return "atomix.segment.truncate.time";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spend to truncate a segment";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return super.getTimerSLOs();
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Time spend to flush segment to disk */
  SEGMENT_FLUSH_TIME {
    @Override
    public String getName() {
      return "atomix.segment.flush.time";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spend to flush segment to disk";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Time spend to flush all dirty segments to disk */
  JOURNAL_FLUSH_TIME {
    @Override
    public String getName() {
      return "atomix.journal.flush.time";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spend to flush all dirty segments to disk";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Number of segments */
  SEGMENT_COUNT {
    @Override
    public String getName() {
      return "atomix.segment.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Number of segments";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Time taken to open the journal */
  JOURNAL_OPERATION_DURATION {
    @Override
    public String getName() {
      return "atomix.journal.open.time";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Time taken to open the journal";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Time spent to allocate a new segment */
  SEGMENT_ALLOCATION_TIME {
    @Override
    public String getName() {
      return "atomix.segment.allocation.time";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time spent to allocate a new segment";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return super.getTimerSLOs();
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** The rate in KiB at which we append data to the journal */
  APPEND_DATA_RATE {
    @Override
    public String getName() {
      return "atomix.journal.append.data.rate";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "The rate in KiB at which we append data to the journal";
    }

    @Override
    public String getBaseUnit() {
      return "KiB";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** The rate at which we append entries in the journal, by entry count */
  APPEND_RATE {
    @Override
    public String getName() {
      return "atomix.journal.append.rate";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "The rate at which we append entries in the journal, by entry count";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Distribution of time spent appending journal records, excluding flushing */
  APPEND_LATENCY {
    @Override
    public String getName() {
      return "atomix.journal.append.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Distribution of time spent appending journal records, excluding flushing";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return super.getTimerSLOs();
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  },
  /** Distribution of time spent seeking to a specific index */
  SEEK_LATENCY {
    private final Duration[] buckets =
        Stream.of(
                100,
                1_000,
                5_000,
                10_0000,
                25_000,
                50_000,
                75_000,
                100_0000,
                250_000,
                500_000,
                75_000,
                1_000 * 1000,
                2_500 * 1000,
                5_000 * 1000)
            .map(micros -> Duration.of(micros, ChronoUnit.MICROS))
            .toArray(Duration[]::new);

    @Override
    public String getName() {
      return "atomix.journal.seek.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Distribution of time spent seeking to a specific index";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return buckets;
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {PartitionKeyNames.PARTITION};
    }
  };
}

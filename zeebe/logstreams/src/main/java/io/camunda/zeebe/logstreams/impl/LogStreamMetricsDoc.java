/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Flow control metrics for the log storage appender. */
@SuppressWarnings("NullableProblems")
public enum LogStreamMetricsDoc implements ExtendedMeterDocumentation {
  /** The count of records passing through the flow control, organized by context and outcome */
  FLOW_CONTROL_OUTCOME {
    @Override
    public String getDescription() {
      return "The count of records passing through the flow control, organized by context and outcome";
    }

    @Override
    public String getName() {
      return "zeebe.flow.control";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return super.getKeyNames();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of deferred appends due to backpressure */
  TOTAL_DEFERRED_APPEND_COUNT {
    @Override
    public String getDescription() {
      return "Number of deferred appends due to backpressure";
    }

    @Override
    public String getName() {
      return "zeebe.deferred.append.count.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of tries to append */
  TOTAL_APPEND_TRY_COUNT {
    @Override
    public String getDescription() {
      return "Number of tries to append";
    }

    @Override
    public String getName() {
      return "zeebe.try.to.append.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Current number of append inflight */
  INFLIGHT_APPENDS {
    @Override
    public String getDescription() {
      return "Current number of append inflight";
    }

    @Override
    public String getName() {
      return "zeebe.backpressure.inflight.append.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of requests received */
  TOTAL_RECEIVED_REQUESTS {
    @Override
    public String getDescription() {
      return "Number of requests received";
    }

    @Override
    public String getName() {
      return "zeebe.received.request.count.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of requests dropped due to backpressure */
  TOTAL_DROPPED_REQUESTS {
    @Override
    public String getDescription() {
      return "Number of requests dropped due to backpressure";
    }

    @Override
    public String getName() {
      return "zeebe.dropped.request.count.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Current number of request inflight */
  INFLIGHT_REQUESTS {
    @Override
    public String getDescription() {
      return "Current number of request inflight";
    }

    @Override
    public String getName() {
      return "zeebe.backpressure.inflight.requests.count";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Current limit for number of inflight requests */
  REQUEST_LIMIT {
    @Override
    public String getDescription() {
      return "Current limit for number of inflight requests";
    }

    @Override
    public String getName() {
      return "zeebe.backpressure.requests.limit";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Current limit for number of inflight appends */
  CURRENT_LIMIT {
    @Override
    public String getDescription() {
      return "Current limit for number of inflight appends";
    }

    @Override
    public String getName() {
      return "zeebe.backpressure.append.limit";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The last committed position */
  LAST_COMMITTED_POSITION {
    @Override
    public String getDescription() {
      return "The last committed position";
    }

    @Override
    public String getName() {
      return "zeebe.log.appender.last.committed.position";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The last appended position by the appender */
  LAST_WRITTEN_POSITION {
    @Override
    public String getDescription() {
      return "The last appended position by the appender";
    }

    @Override
    public String getName() {
      return "zeebe.log.appender.last.appended.position";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Latency to append an event to the log in seconds */
  WRITE_LATENCY {
    @Override
    public String getDescription() {
      return "Latency to append an event to the log in seconds";
    }

    @Override
    public String getName() {
      return "zeebe.log.appender.append.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Latency to commit an event to the log in seconds */
  COMMIT_LATENCY {
    @Override
    public String getDescription() {
      return "Latency to commit an event to the log in seconds";
    }

    @Override
    public String getName() {
      return "zeebe.log.appender.commit.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Count of records appended per partition, record type, value type, and intent */
  RECORD_APPENDED {
    @Override
    public String getDescription() {
      return "Count of records appended per partition, record type, value type, and intent";
    }

    @Override
    public String getName() {
      return "zeebe.log.appender.record.appended";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return RecordAppendedKeyNames.values();
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /**
   * The current load of the partition. Determined by observed write rate compared to the write rate
   * limit.
   */
  PARTITION_LOAD {
    @Override
    public String getDescription() {
      return """
        The current load of the partition. Determined by observed write rate compared to the write \
        rate limit""";
    }

    @Override
    public String getName() {
      return "zeebe.flow.control.partition.load";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The current write rate limit */
  WRITE_RATE_LIMIT {
    @Override
    public String getDescription() {
      return "The current write rate limit";
    }

    @Override
    public String getName() {
      return "zeebe.flow.control.write.rate.limit";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The maximum write rate limit */
  WRITE_RATE_MAX_LIMIT {
    @Override
    public String getDescription() {
      return "The maximum write rate limit";
    }

    @Override
    public String getName() {
      return "zeebe.flow.control.write.rate.maximum";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** The rate of exporting records from the log appender */
  EXPORTING_RATE {
    @Override
    public String getDescription() {
      return "The rate of exporting records from the log appender";
    }

    @Override
    public String getName() {
      return "zeebe.flow.control.exporting.rate";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  /** Tags/labels associated with the {@link #RECORD_APPENDED} metric. */
  public enum RecordAppendedKeyNames implements KeyName {
    /**
     * The record type of the appended record; see {@link
     * io.camunda.zeebe.protocol.record.RecordType} for possible values
     */
    RECORD_TYPE {
      @Override
      public String asString() {
        return "recordType";
      }
    },

    /**
     * The value type of the record value of the appended record; see {@link
     * io.camunda.zeebe.protocol.record.ValueType} for possible values
     */
    VALUE_TYPE {
      @Override
      public String asString() {
        return "valueType";
      }
    },

    /**
     * The intent of the command or event that was appended; could be any value from one of the many
     * enums which implement {@link io.camunda.zeebe.protocol.record.intent.Intent}
     */
    INTENT {
      @Override
      public String asString() {
        return "intent";
      }
    }
  }

  /** Possible tags for the {@link #FLOW_CONTROL_OUTCOME} metric */
  public enum FlowControlKeyNames implements KeyName {
    /** Within which context the */
    CONTEXT {
      @Override
      public String asString() {
        return "context";
      }
    },

    OUTCOME {
      @Override
      public String asString() {
        return "outcome";
      }
    }
  }

  public enum FlowControlOutcome {
    ACCEPTED("accepted"),
    WRITE_RATE_LIMIT_EXHAUSTED("writeRateLimitExhausted"),
    REQUEST_LIMIT_EXHAUSTED("requestLimitExhausted");

    private final String value;

    FlowControlOutcome(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public enum FlowControlContext {
    USER_COMMAND("userCommand"),
    PROCESSING_RESULT("processingResult"),
    INTER_PARTITION("interPartition"),
    SCHEDULED("scheduled"),
    INTERNAL("internal");

    private final String value;

    FlowControlContext(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}

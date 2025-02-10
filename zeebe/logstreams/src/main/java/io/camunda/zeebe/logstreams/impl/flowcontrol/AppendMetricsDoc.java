/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

/** Flow control metrics for the log storage appender. */
@SuppressWarnings("NullableProblems")
public enum AppendMetricsDoc implements ExtendedMeterDocumentation {
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
  },
  /** Current number of append inflight */
  CURRENT_INFLIGHT {
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
}

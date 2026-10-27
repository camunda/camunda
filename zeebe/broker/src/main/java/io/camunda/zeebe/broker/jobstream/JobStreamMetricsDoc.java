/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Type;

/**
 * Documents metrics used by the job stream feature on the server side. See {@link JobStreamMetrics}
 * for more.
 */
@SuppressWarnings("NullableProblems")
public enum JobStreamMetricsDoc implements ExtendedMeterDocumentation {
  /** Number of open job streams in broker */
  STREAM_COUNT {
    @Override
    public String getName() {
      return "zeebe.broker.open.job.stream.count";
    }

    @Override
    public Meter.Type getType() {
      return Meter.Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Number of open job streams in broker";
    }
  },

  /** Total number of jobs pushed to all streams */
  PUSH_SUCCESS_COUNT {
    @Override
    public String getName() {
      return "zeebe.broker.jobs.pushed.count";
    }

    @Override
    public Meter.Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Total number of jobs pushed to all streams";
    }
  },

  /** Total number of failures when pushing jobs to the streams */
  PUSH_FAILED_COUNT {
    @Override
    public String getName() {
      return "zeebe.broker.jobs.push.fail.count";
    }

    @Override
    public Meter.Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Total number of failures when pushing jobs to the streams";
    }
  },

  /** Total number of failed attempts when pushing jobs to the streams, grouped by error code */
  PUSH_TRY_FAILED_COUNT {
    @Override
    public String getName() {
      return "zeebe.broker.jobs.fail.try.count";
    }

    @Override
    public Meter.Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Total number of failed attempts when pushing jobs to the streams, grouped by error code";
    }

    @Override
    public KeyName[] getKeyNames() {
      return PushTryFailedCodeKeyNames.values();
    }
  };

  /** Possible error codes for use with {@link #PUSH_TRY_FAILED_COUNT} */
  enum PushTryFailedCodeKeyNames implements KeyName {
    INTERNAL {
      @Override
      public String asString() {
        return "INTERNAL";
      }
    },

    NOT_FOUND {
      @Override
      public String asString() {
        return "NOT_FOUND";
      }
    },

    INVALID {
      @Override
      public String asString() {
        return "INVALID";
      }
    },

    MALFORMED {
      @Override
      public String asString() {
        return "MALFORMED";
      }
    },

    EXHAUSTED {
      @Override
      public String asString() {
        return "EXHAUSTED";
      }
    },

    BLOCKED {
      @Override
      public String asString() {
        return "BLOCKED";
      }
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;

/** {@link SuspensionMetricsDoc} documents process instance and job suspension metrics. */
@SuppressWarnings("NullableProblems")
public enum SuspensionMetricsDoc implements ExtendedMeterDocumentation {
  /** Number of process instance suspension lifecycle events */
  SUSPENSION_EVENTS {
    @Override
    public String getName() {
      return "zeebe.process.instance.suspension.events.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of process instance suspension lifecycle events";
    }

    @Override
    public KeyName[] getKeyNames() {
      return ACTION_KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of job suspension lifecycle events */
  JOB_SUSPENSION_EVENTS {
    @Override
    public String getName() {
      return "zeebe.job.suspension.events.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of job suspension lifecycle events";
    }

    @Override
    public KeyName[] getKeyNames() {
      return ACTION_KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Number of buffered command lifecycle events */
  BUFFERED_COMMAND_EVENTS {
    @Override
    public String getName() {
      return "zeebe.buffered.commands.events.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of buffered command lifecycle events";
    }

    @Override
    public KeyName[] getKeyNames() {
      return ACTION_KEY_NAMES;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  },

  /** Wall-clock time from RESUME command to RESUMED event per process instance */
  RESUME_DURATION {
    private static final Duration[] BUCKETS =
        new Duration[] {
          Duration.ofMillis(10),
          Duration.ofMillis(100),
          Duration.ofMillis(500),
          Duration.ofSeconds(1),
          Duration.ofSeconds(5),
          Duration.ofSeconds(10),
          Duration.ofSeconds(30),
          Duration.ofMinutes(1),
          Duration.ofMinutes(5)
        };

    @Override
    public String getName() {
      return "zeebe.process.instance.resume.duration";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Wall-clock time from RESUME command to RESUMED event per process instance";
    }

    @Override
    public Duration[] getTimerSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getAdditionalKeyNames() {
      return PartitionKeyNames.values();
    }
  };

  private static final KeyName[] ACTION_KEY_NAMES = new KeyName[] {SuspensionKeyNames.ACTION};

  /** Tags/label values used by the suspension metrics. */
  public enum SuspensionKeyNames implements KeyName {
    /**
     * The suspension lifecycle action; see {@link SuspensionAction} and {@link
     * BufferedCommandAction} for possible values.
     */
    ACTION {
      @Override
      public String asString() {
        return "action";
      }
    }
  }

  public enum SuspensionAction {
    SUSPENDED,
    RESUMED;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  public enum BufferedCommandAction {
    BUFFERED,
    DRAINED,
    DROPPED;

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }
}

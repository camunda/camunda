/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@SuppressWarnings("NullableProblems")
public enum ActorMetricsDoc implements ExtendedMeterDocumentation {
  /** Execution time of a certain actor task */
  EXECUTION_LATENCY {
    private static final Duration[] TIMER_SLOS =
        MicrometerUtil.exponentialBucketDuration(100, 4, 10, ChronoUnit.MICROS);

    @Override
    public String getName() {
      return "zeebe.actor.task.execution.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Execution time of a certain actor task";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {ActorMetricsKeyName.ACTOR_NAME};
    }

    @Override
    public Duration[] getTimerSLOs() {
      return TIMER_SLOS;
    }
  },
  /** Time between scheduling and executing a job */
  SCHEDULING_LATENCY {
    private static final Duration[] TIMER_SLOS =
        MicrometerUtil.exponentialBucketDuration(1, 4, 12, ChronoUnit.MICROS);

    @Override
    public String getName() {
      return "zeebe.actor.job.scheduling.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time between scheduling and executing a job";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {ActorMetricsKeyName.SUBSCRIPTION_TYPE};
    }

    @Override
    public Duration[] getTimerSLOs() {
      return TIMER_SLOS;
    }
  },
  /** Number of times a certain actor task was executed successfully */
  EXECUTION_COUNT {
    @Override
    public String getName() {
      return "zeebe.actor_task_execution_count";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of times a certain actor task was executed successfully";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {ActorMetricsKeyName.ACTOR_NAME};
    }
  },
  /** The length of the job queue for an actor task */
  JOB_QUEUE_LENGTH {
    @Override
    public String getName() {
      return "zeebe.actor.task.queue.length";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The length of the job queue for an actor task";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {ActorMetricsKeyName.ACTOR_NAME};
    }
  };

  public enum ActorMetricsKeyName implements KeyName {
    /** The type of the subscription, see {@link ActorMetrics.SubscriptionType} */
    SUBSCRIPTION_TYPE {
      @Override
      public String asString() {
        return "subscriptionType";
      }
    },
    /** The name of the actor */
    ACTOR_NAME {
      @Override
      public String asString() {
        return "actorName";
      }
    }
  }
}

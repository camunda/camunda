/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum RaftRoleMetricsDoc implements ExtendedMeterDocumentation {
  /** Shows current role */
  ROLE {
    @Override
    public String getName() {
      return "atomix.role";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Shows current role";
    }

    @Override
    public KeyName[] getKeyNames() {
      return COMMON_KEY_NAMES;
    }
  },
  /** Count of missing heartbeats */
  HEARTBEAT_MISS {
    @Override
    public String getName() {
      return "atomix.heartbeat.miss.count";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Count of missing heartbeats";
    }

    @Override
    public KeyName[] getKeyNames() {
      return COMMON_KEY_NAMES;
    }
  },
  /** Time between heartbeats */
  HEARTBEAT_TIME {
    @Override
    public String getName() {
      return "atomix.heartbeat.time.in.s";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Time between heartbeats";
    }

    @Override
    public KeyName[] getKeyNames() {
      return COMMON_KEY_NAMES;
    }
  },
  /** Duration for election */
  ELECTION_LATENCY {
    @Override
    public String getName() {
      return "atomix.election.latency.in.ms";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Duration for election";
    }

    @Override
    public KeyName[] getKeyNames() {
      return COMMON_KEY_NAMES;
    }
  };

  private static final KeyName[] COMMON_KEY_NAMES =
      new KeyName[] {RaftKeyNames.PARTITION_GROUP, PartitionKeyNames.PARTITION};
}

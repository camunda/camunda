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
public enum RaftRequestMetricsDoc implements ExtendedMeterDocumentation {
  /** Number of raft requests received */
  RAFT_MESSAGE_RECEIVED {
    @Override
    public String getName() {
      return "atomix.raft.messages.received";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of raft requests received";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        RaftKeyNames.TYPE, RaftKeyNames.PARTITION_GROUP, PartitionKeyNames.PARTITION
      };
    }
  },
  /** Number of raft requests send */
  RAFT_MESSAGE_SEND {
    @Override
    public String getName() {
      return "atomix.raft.messages.send";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public String getDescription() {
      return "Number of raft requests send";
    }

    @Override
    public KeyName[] getKeyNames() {
      return new KeyName[] {
        RaftKeyNames.TYPE,
        RaftKeyNames.TYPE,
        RaftKeyNames.PARTITION_GROUP,
        PartitionKeyNames.PARTITION
      };
    }
  }
}

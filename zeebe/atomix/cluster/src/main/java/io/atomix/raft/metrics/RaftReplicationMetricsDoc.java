/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

public enum RaftReplicationMetricsDoc implements ExtendedMeterDocumentation {
  /** The commit index */
  COMMIT_INDEX {
    @Override
    public String getName() {
      return "partition.raft.commit.index";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The commit index";
    }
  },
  /** The index of last entry appended to the log */
  APPEND_INDEX {
    @Override
    public String getName() {
      return "partition.raft.append.index";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The index of last entry appended to the log";
    }
  }
}

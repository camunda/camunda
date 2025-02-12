/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum TopologyMetricsDoc implements ExtendedMeterDocumentation {
  /** The version of the cluster topology */
  TOPOLOGY_VERSION {
    @Override
    public String getName() {
      return "zeebe.cluster.topology.version";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The version of the cluster topology";
    }
  },
  /** The id of the cluster topology change plan */
  CHANGE_ID {
    @Override
    public String getName() {
      return "zeebe.cluster.changes.id";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The id of the cluster topology change plan";
    }
  },
  /** The state of the current cluster topology */
  CHANGE_STATUS {
    @Override
    public String getName() {
      return "zeebe.cluster.changes.status";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The state of the current cluster topology";
    }

    @Override
    public KeyName[] getKeyNames() {
      return TopologyMetricsKeyName.values();
    }
  },
  /** The version of the cluster topology change plan */
  CHANGE_VERSION {
    @Override
    public String getName() {
      return "zeebe.cluster.changes.version";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "The version of the cluster topology change plan";
    }
  },
  /** Number of pending changes in the current change plan */
  PENDING_OPERATIONS {
    @Override
    public String getName() {
      return "zeebe.cluster.changes.operations.pending";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Number of pending changes in the current change plan";
    }
  },
  /** Number of completed changes in the current change plan */
  COMPLETED_OPERATIONS {
    @Override
    public String getName() {
      return "zeebe.cluster.changes.operations.completed";
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Number of completed changes in the current change plan";
    }
  };

  @SuppressWarnings("NullableProblems")
  public enum TopologyMetricsKeyName implements KeyName {
    CLUSTER_CHANGE_STATUS {
      @Override
      public String asString() {
        return "zeebe_cluster_changes_status";
      }
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

public enum BatchOperationMetricsDoc implements ExtendedMeterDocumentation {

  /** Number of executed batch operations events */
  EXECUTED_LIFECYCLE_EVENTS {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          BatchOperationKeyNames.ACTION,
          PartitionKeyNames.PARTITION,
          BatchOperationKeyNames.BATCH_OPERATION_TYPE,
          BatchOperationKeyNames.ORGANIZATION_ID
        };

    @Override
    public String getDescription() {
      return "Total number of executed batch operation lifecycle events (created, started, executed, completed, failed, cancelled, suspended, resumed) per partition and batch operation type";
    }

    @Override
    public String getName() {
      return "zeebe.batchoperations.events.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  },

  EXECUTED_QUERIES {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {PartitionKeyNames.PARTITION, BatchOperationKeyNames.ORGANIZATION_ID};

    @Override
    public String getDescription() {
      return "Number of executed queries to the secondary database for batch operation";
    }

    @Override
    public String getName() {
      return "zeebe.batchoperations.queries.total";
    }

    @Override
    public Type getType() {
      return Type.COUNTER;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  },

  ITEMS_PER_PARTITION {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          PartitionKeyNames.PARTITION,
          BatchOperationKeyNames.BATCH_OPERATION_TYPE,
          BatchOperationKeyNames.ORGANIZATION_ID
        };

    @Override
    public String getName() {
      return "zeebe.batchoperations.items.total";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public String getDescription() {
      return "Number of total items in batch operations per partition";
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  },

  BATCH_OPERATION_LATENCY {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {
          PartitionKeyNames.PARTITION,
          BatchOperationKeyNames.BATCH_OPERATION_KEY,
          BatchOperationKeyNames.LATENCY,
          BatchOperationKeyNames.BATCH_OPERATION_TYPE,
          BatchOperationKeyNames.ORGANIZATION_ID
        };

    @Override
    public String getBaseUnit() {
      return "ms";
    }

    @Override
    public String getName() {
      return "zeebe.batchoperations.latency";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }

    @Override
    public String getDescription() {
      return "Latencies (totalLatency, totalExecutionLatency, totalQueryLatency, startExecuteLatency, executeCycleLatency) which are measured for batch operations";
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  };

  /** Tags/label values possibly used by the engine metrics. */
  public enum BatchOperationKeyNames implements KeyName {

    /**
     * The action that modified the given series; see {@link BatchOperationAction} for possible
     * values.
     */
    ACTION {
      @Override
      public String asString() {
        return "action";
      }
    },

    /** The latency of the batch operation. see {@link BatchOperationLatency} for possible */
    LATENCY {
      @Override
      public String asString() {
        return "batchOperationLatency";
      }
    },

    /** The key of the batch operation. */
    BATCH_OPERATION_KEY {
      @Override
      public String asString() {
        return "batchOperationKey";
      }
    },

    /**
     * The type of the batch operation. See {@link
     * io.camunda.zeebe.protocol.record.value.BatchOperationType} for values.
     */
    BATCH_OPERATION_TYPE {
      @Override
      public String asString() {
        return "type";
      }
    },

    /**
     * Metrics that are annotated with this label are vitally important for usage tracking and
     * data-based decision-making as part of Camunda's SaaS offering.
     *
     * <p>DO NOT REMOVE this label from existing metrics without previous discussion within the
     * team.
     *
     * <p>At the same time, NEW METRICS MAY NOT NEED THIS label. In that case, it is preferable to
     * not add this label to a metric as Prometheus best practices warn against using labels with a
     * high cardinality of possible values.
     */
    ORGANIZATION_ID {
      @Override
      public String asString() {
        return "organizationId";
      }
    }
  }

  public enum BatchOperationAction {
    CREATED("created"),
    QUERY("query"),
    STARTED("started"),
    CHUNK_CREATED("chunkCreated"),
    EXECUTED("execute"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    SUSPENDED("suspended"),
    RESUMED("resumed");

    private final String label;

    BatchOperationAction(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  public enum BatchOperationLatency {
    TOTAL_LATENCY("totalLatency"),
    TOTAL_EXECUTION_LATENCY("totalExecutionLatency"),
    TOTAL_QUERY_LATENCY("totalQueryLatency"),
    START_EXECUTE_LATENCY("startExecuteLatency"),
    EXECUTE_CYCLE_LATENCY("executeCycleLatency");

    private final String label;

    BatchOperationLatency(final String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

@SuppressWarnings("NullableProblems")
public enum VariableMetricsDoc implements ExtendedMeterDocumentation {

  /** Total bytes of variable values written on VARIABLE:CREATED events. */
  VARIABLE_CREATED_BYTES {
    private static final KeyName[] KEY_NAMES =
        new KeyName[] {PartitionKeyNames.PARTITION, VariableKeyNames.BPMN_PROCESS_ID};

    @Override
    public String getDescription() {
      return "Total bytes of variable values written on VARIABLE:CREATED events.";
    }

    @Override
    public String getName() {
      return "zeebe.variable.created.bytes";
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

  /** Size distribution of variable values written on VARIABLE:CREATED events. */
  VARIABLE_CREATED_SIZE {
    private static final double[] BUCKETS = {64, 256, 1024, 4096, 16384, 65536, 262144, 1048576};

    private static final KeyName[] KEY_NAMES =
        new KeyName[] {PartitionKeyNames.PARTITION, VariableKeyNames.BPMN_PROCESS_ID};

    @Override
    public String getDescription() {
      return "Size distribution of variable values written on VARIABLE:CREATED events.";
    }

    @Override
    public String getName() {
      return "zeebe.variable.created.size";
    }

    @Override
    public Type getType() {
      return Type.DISTRIBUTION_SUMMARY;
    }

    @Override
    public double[] getDistributionSLOs() {
      return BUCKETS;
    }

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }
  };

  /** Tags/label keys used by variable metrics. */
  public enum VariableKeyNames implements KeyName {
    /** The BPMN process id of the process definition the variable belongs to. */
    BPMN_PROCESS_ID {
      @Override
      public String asString() {
        return "bpmnProcessId";
      }
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.common.docs.KeyName;
import io.micrometer.core.instrument.Meter.Type;

public enum WorkerMetricsDoc implements ExtendedMeterDocumentation {
  /**
   * How late a job was activated again after its timeout elapsed. Only recorded when the worker
   * does not complete jobs. Measured on the client clock, from the previous activation of the same
   * job. Zero means the job came back on time.
   */
  JOB_REACTIVATION_DELAY {
    private static final KeyName[] KEY_NAMES = new KeyName[] {WorkerMetricKeyNames.JOB_TYPE};

    @Override
    public KeyName[] getKeyNames() {
      return KEY_NAMES;
    }

    @Override
    public String getDescription() {
      return "How late a job was activated again after its timeout elapsed, measured on the client clock.";
    }

    @Override
    public String getName() {
      return "job.reactivation.delay";
    }

    @Override
    public Type getType() {
      return Type.TIMER;
    }
  };

  public enum WorkerMetricKeyNames implements KeyName {

    /** The type of the activated job */
    JOB_TYPE {
      @Override
      public String asString() {
        return "job_type";
      }
    }
  }
}

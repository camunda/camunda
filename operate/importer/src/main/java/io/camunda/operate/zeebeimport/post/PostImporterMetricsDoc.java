/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post;

import io.camunda.operate.Metrics;
import io.camunda.zeebe.util.micrometer.ExtendedMeterDocumentation;
import io.micrometer.core.instrument.Meter.Type;

/**
 * {@link PostImporterMetricsDoc} documents all post importer specific metrics following the Camunda
 * metrics guide.
 */
@SuppressWarnings("NullableProblems")
public enum PostImporterMetricsDoc implements ExtendedMeterDocumentation {

  /**
   * Post importer queue size metric.
   *
   * <p>This gauge metric tracks the current size of the post importer queue for each partition. It
   * helps monitor if the post importer is stuck or overloaded, which is critical for operational
   * monitoring.
   *
   * <p><strong>Use case:</strong> Quickly determine if the post importer is experiencing issues
   * without requiring manual polling. This was identified as a need in support case SUPPORT-27255.
   *
   * <p><strong>Update frequency:</strong> Updated on each processing batch request when {@code
   * getPendingIncidents()} is called.
   *
   * <p><strong>Alerting considerations:</strong> High values may indicate that the post importer is
   * unable to keep up with the incoming incident processing workload.
   */
  POST_IMPORTER_QUEUE_SIZE {
    @Override
    public String getName() {
      return Metrics.GAUGE_POST_IMPORTER_QUEUE_SIZE;
    }

    @Override
    public Type getType() {
      return Type.GAUGE;
    }

    @Override
    public String getDescription() {
      return "Current size of the post importer queue for processing pending incidents";
    }
  };
}

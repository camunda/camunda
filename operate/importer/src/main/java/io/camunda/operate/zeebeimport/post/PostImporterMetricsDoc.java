/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.post;

import io.camunda.operate.Metrics;

/**
 * Documentation for post importer metrics following the Camunda metrics guide.
 *
 * <p>This class serves as documentation for all metrics related to the post importer functionality,
 * providing clear descriptions and usage guidelines for each metric.
 */
public final class PostImporterMetricsDoc {

  private PostImporterMetricsDoc() {
    // Utility class
  }

  /**
   * Post importer queue size metric documentation.
   *
   * <p>This gauge metric tracks the current size of the post importer queue for each partition. It
   * helps monitor if the post importer is stuck or overloaded, which is critical for operational
   * monitoring.
   *
   * <p><strong>Use case:</strong> Quickly determine if the post importer is experiencing issues
   * without requiring manual polling. This was identified as a need in support case SUPPORT-27255.
   *
   * <p><strong>Type:</strong> Gauge (current value at a specific moment)
   *
   * <p><strong>Tags:</strong>
   *
   * <ul>
   *   <li>partition - The partition ID for which the queue size is measured
   * </ul>
   *
   * <p><strong>Update frequency:</strong> Updated on each processing batch request when {@code
   * getPendingIncidents()} is called.
   *
   * <p><strong>Alerting considerations:</strong> High values may indicate that the post importer is
   * unable to keep up with the incoming incident processing workload.
   */
  public static final class PostImporterQueueSize {
    public static final String NAME = Metrics.GAUGE_POST_IMPORTER_QUEUE_SIZE;
    public static final String DESCRIPTION =
        "Current size of the post importer queue for processing pending incidents";

    private PostImporterQueueSize() {
      // Utility class
    }

    public String getName() {
      return NAME;
    }

    public String getDescription() {
      return DESCRIPTION;
    }
  }
}

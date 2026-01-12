/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

/** Functional interface for consuming job metrics entries. */
@FunctionalInterface
public interface MetricsConsumer {

  /**
   * Accepts a job metrics entry.
   *
   * @param jobTypeIndex the encoded integer index for job type
   * @param tenantIdIndex the encoded integer index for tenant ID
   * @param workerNameIndex the encoded integer index for worker name
   * @param metrics array of StatusMetrics (one per JobStatus enum value)
   */
  void accept(int jobTypeIndex, int tenantIdIndex, int workerNameIndex, StatusMetrics[] metrics);
}

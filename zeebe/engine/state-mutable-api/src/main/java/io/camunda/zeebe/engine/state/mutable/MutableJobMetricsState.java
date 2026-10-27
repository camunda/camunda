/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;

/** Mutable interface for managing job metrics state. */
public interface MutableJobMetricsState
    extends io.camunda.zeebe.engine.state.immutable.JobMetricsState {

  /**
   * Increments the metric for the given combination by 1.
   *
   * <ul>
   *   <li>Encodes strings to integers using STRING_ENCODING column family (auto-creates if new)
   *   <li>Retrieves or creates metrics array in METRICS column family
   *   <li>Increments count by 1 for the specified status
   *   <li>Updates lastUpdatedAt to current timestamp
   *   <li>Updates META column family counters appropriately
   * </ul>
   *
   * @param status the job status to increment
   */
  void incrementMetric(final JobRecord jobRecord, JobMetricsExportState status);

  /**
   * Clears all data:
   *
   * <ul>
   *   <li>Delete ALL keys/values in METRICS column family
   *   <li>Delete ALL keys/values in STRING_ENCODING column family
   *   <li>Delete ALL keys/values in METADATA column family
   * </ul>
   */
  void cleanUp();
}

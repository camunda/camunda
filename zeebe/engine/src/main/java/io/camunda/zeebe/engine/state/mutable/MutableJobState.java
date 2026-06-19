/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;

public interface MutableJobState extends JobState {

  /**
   * Pre-prioritization create path. Inserts the job into the legacy {@code JOB_ACTIVATABLE} column
   * family, ignoring the {@code priority} field on the record. Used by {@code JobCreatedV2Applier}
   * so that records stamped with {@code recordVersion=2} (selected by the write side when {@link
   * io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability#JOB_PRIORITIZATION}
   * is inactive) produce identical state to a pre-PR broker.
   */
  void create(long key, JobRecord record);

  /**
   * Priority-aware create path. Inserts the job into {@code JOB_ACTIVATABLE_BY_PRIORITY} with the
   * record's priority as the sort key. Used by {@code JobCreatedV3Applier}, which is selected only
   * when ECV has activated {@link
   * io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability#JOB_PRIORITIZATION}.
   */
  void createWithPriorityActivation(long key, JobRecord record);

  void activate(long key, JobRecord record);

  void recurAfterBackoff(long key, JobRecord record);

  void timeout(long key, JobRecord record);

  void complete(long key, JobRecord record);

  void cancel(long key, JobRecord record);

  void disable(long key, JobRecord record);

  void throwError(long key, JobRecord updatedValue);

  void delete(long key, JobRecord record);

  void fail(long key, JobRecord updatedValue);

  void yield(long key, JobRecord updatedValue);

  void resolve(long key, JobRecord updatedValue);

  JobRecord updateJobRetries(long jobKey, int retries);

  void cleanupTimeoutsWithoutJobs();

  void cleanupBackoffsWithoutJobs();

  void updateJobDeadline(long jobKey, long newDeadline);

  void updateJobPriority(long jobKey, int newPriority);

  void migrate(long key, JobRecord record);

  void restoreBackoff();
}

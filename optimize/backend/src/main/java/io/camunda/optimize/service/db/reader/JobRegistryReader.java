/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import java.util.List;
import java.util.Optional;

public interface JobRegistryReader {

  /**
   * Returns up to {@code limit} of the oldest QUEUED job registry entries, across all job types,
   * ordered oldest first.
   */
  List<JobRegistryEntryDto> findOldestQueuedJobs(int limit);

  /**
   * Returns the most recent job registry entry for the given jobType, entityType, and entityId, if
   * one exists (in any status).
   */
  Optional<JobRegistryEntryDto> findLastByJobTypeAndEntityId(
      JobType jobType, EntityType entityType, String entityId);

  /**
   * Returns the distinct entityIds of up to {@code limit} job registry entries for the given
   * jobType and entityType regardless of the status, ordered newest-created first. At most one (the
   * newest) entry per entityId is considered.
   */
  List<String> findNewestEntityIds(JobType jobType, EntityType entityType, int limit);
}

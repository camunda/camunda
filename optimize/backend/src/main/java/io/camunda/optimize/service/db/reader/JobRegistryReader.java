/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;

public abstract class JobRegistryReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryReader.class);

  /** Returns the oldest QUEUED job registry entry of the given jobType. */
  public Optional<JobRegistryEntryDto> findOldestQueuedJob(final JobType jobType) {
    LOG.debug("Fetching oldest QUEUED job registry entry for [{}].", jobType);
    try {
      return performFindOldestQueuedJob(jobType);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Was not able to fetch oldest QUEUED job registry entry for [%s].", jobType);
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  protected abstract Optional<JobRegistryEntryDto> performFindOldestQueuedJob(final JobType jobType)
      throws IOException;
}

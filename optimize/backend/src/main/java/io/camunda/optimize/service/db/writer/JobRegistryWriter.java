/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryUpdateDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.optimize.query.job.TargetEntityType;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;

public abstract class JobRegistryWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobRegistryWriter.class);

  public JobRegistryEntryDto createJobEntry(
      final JobType jobType, final TargetEntityType targetEntityType, final String targetEntityId) {
    final JobRegistryEntryDto entry =
        new JobRegistryEntryDto(jobType, targetEntityType, targetEntityId);
    LOG.debug(
        "Creating job registry entry with id [{}] for [{}] target [{}].",
        entry.getId(),
        jobType,
        targetEntityId);
    try {
      performCreatingJobEntry(entry);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Could not write job registry entry with id [%s] to database.", entry.getId());
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
    return entry;
  }

  public void updateJobStatus(final String id, final JobStatus status, final String errorMessage) {
    LOG.debug("Updating job registry entry [{}] to status [{}].", id, status);
    final OffsetDateTime completedAt =
        (status == JobStatus.COMPLETED || status == JobStatus.FAILED)
            ? LocalDateUtil.getCurrentDateTime()
            : null;
    final JobRegistryEntryUpdateDto update =
        new JobRegistryEntryUpdateDto(status, errorMessage, completedAt);
    try {
      performUpdatingJobStatus(id, update);
    } catch (final IOException e) {
      final String message =
          String.format("Could not update job registry entry with id [%s] in database.", id);
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }

  protected abstract void performCreatingJobEntry(final JobRegistryEntryDto entry)
      throws IOException;

  protected abstract void performUpdatingJobStatus(
      final String id, final JobRegistryEntryUpdateDto update) throws IOException;
}

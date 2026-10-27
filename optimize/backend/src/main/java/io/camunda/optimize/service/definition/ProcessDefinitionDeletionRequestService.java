/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.definition;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionDeletionRequestService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionDeletionRequestService.class);

  private static final String REST_API_IDENTIFIER_NAME = "processDefinitionKey";

  private final ProcessDefinitionReader processDefinitionReader;
  private final JobRegistryReader jobRegistryReader;
  private final JobRegistryWriter jobRegistryWriter;

  public ProcessDefinitionDeletionRequestService(
      final ProcessDefinitionReader processDefinitionReader,
      final JobRegistryReader jobRegistryReader,
      final JobRegistryWriter jobRegistryWriter) {
    this.processDefinitionReader = processDefinitionReader;
    this.jobRegistryReader = jobRegistryReader;
    this.jobRegistryWriter = jobRegistryWriter;
  }

  public void queueProcessDefinitionDeletion(final String processDefinitionId) {
    LOG.info(
        "Received request to delete Optimize data for process definition [{}].",
        processDefinitionId);

    validateIsNumeric(processDefinitionId);
    validateDefinitionExists(processDefinitionId);
    validateNoBlockingJobExists(processDefinitionId);

    jobRegistryWriter.createJobEntry(
        JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionId);
  }

  private void validateIsNumeric(final String processDefinitionId) {
    try {
      Long.parseLong(processDefinitionId);
    } catch (final NumberFormatException e) {
      throw new BadRequestException(
          String.format("%s [%s] must be numeric.", REST_API_IDENTIFIER_NAME, processDefinitionId));
    }
  }

  private void validateDefinitionExists(final String processDefinitionId) {
    if (!processDefinitionReader.processDefinitionExists(processDefinitionId)) {
      throw new NotFoundException(
          String.format(
              "No process definition found for %s [%s].",
              REST_API_IDENTIFIER_NAME, processDefinitionId));
    }
  }

  private void validateNoBlockingJobExists(final String processDefinitionId) {
    final Optional<JobRegistryEntryDto> existingEntry =
        jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionId);
    final boolean deleteJobAlreadyExists =
        existingEntry.filter(entry -> entry.getStatus() == JobStatus.QUEUED).isPresent();
    if (deleteJobAlreadyExists) {
      throw new OptimizeConflictException(
          String.format(
              "A deletion job for %s [%s] already exists with status [%s].",
              REST_API_IDENTIFIER_NAME, processDefinitionId, existingEntry.get().getStatus()));
    }
  }
}

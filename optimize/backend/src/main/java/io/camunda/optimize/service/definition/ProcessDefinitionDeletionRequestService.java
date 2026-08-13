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

  public void queueProcessDefinitionDeletion(final String processDefinitionKey) {
    LOG.info(
        "Received request to delete Optimize data for process definition [{}].",
        processDefinitionKey);

    validateIsNumeric(processDefinitionKey);
    validateDefinitionExists(processDefinitionKey);
    validateNoBlockingJobExists(processDefinitionKey);

    jobRegistryWriter.createJobEntry(
        JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionKey);
  }

  private void validateIsNumeric(final String processDefinitionKey) {
    try {
      Long.parseLong(processDefinitionKey);
    } catch (final NumberFormatException e) {
      throw new BadRequestException(
          String.format("processDefinitionKey [%s] must be numeric.", processDefinitionKey));
    }
  }

  private void validateDefinitionExists(final String processDefinitionKey) {
    if (processDefinitionReader.getProcessDefinition(processDefinitionKey, false).isEmpty()) {
      throw new NotFoundException(
          String.format(
              "No process definition found for processDefinitionKey [%s].", processDefinitionKey));
    }
  }

  private void validateNoBlockingJobExists(final String processDefinitionKey) {
    final Optional<JobRegistryEntryDto> existingEntry =
        jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionKey);
    final boolean deleteJobAlreadyExists =
        existingEntry.filter(entry -> entry.getStatus() != JobStatus.FAILED).isPresent();
    if (deleteJobAlreadyExists) {
      throw new OptimizeConflictException(
          String.format(
              "A deletion job for processDefinitionKey [%s] already exists with status [%s].",
              processDefinitionKey, existingEntry.get().getStatus()));
    }
  }
}

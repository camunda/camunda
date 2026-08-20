/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ProcessDefinitionDeletionRequestServiceTest {

  private static final String PROCESS_DEFINITION_ID = "123456789";

  private final ProcessDefinitionReader processDefinitionReader =
      mock(ProcessDefinitionReader.class);
  private final JobRegistryReader jobRegistryReader = mock(JobRegistryReader.class);
  private final JobRegistryWriter jobRegistryWriter = mock(JobRegistryWriter.class);

  private final ProcessDefinitionDeletionRequestService underTest =
      new ProcessDefinitionDeletionRequestService(
          processDefinitionReader, jobRegistryReader, jobRegistryWriter);

  @Test
  public void shouldQueueJobWhenIdIsValidAndNoBlockingEntryExists() {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.empty());

    // when
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID);

    // then
    verify(jobRegistryWriter)
        .createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
  }

  @Test
  public void shouldQueueBothJobsWhenConcurrentRequestsRaceThePreCreationCheck() {
    // given: two requests both check for a blocking entry before either has written its own,
    // modeling the race window between the read and the write
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.empty());

    // when
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID);
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID);

    // then: both requests are allowed to queue their own job; concurrent duplicate deletion
    // requests are accepted
    verify(jobRegistryWriter, times(2))
        .createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
  }

  @Test
  public void shouldRejectNonNumericId() {
    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.queueProcessDefinitionDeletion("not-a-number"));

    // then
    assertThat(thrown).isInstanceOf(BadRequestException.class);
    verify(jobRegistryWriter, never()).createJobEntry(any(), any(), any());
  }

  @Test
  public void shouldRejectIdNotFoundInProcessDefinitionIndex() {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(false);

    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID));

    // then
    assertThat(thrown).isInstanceOf(NotFoundException.class);
    verify(jobRegistryWriter, never()).createJobEntry(any(), any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"QUEUED", "COMPLETED"})
  public void shouldRejectWhenBlockingEntryAlreadyExists(final JobStatus blockingStatus) {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    final JobRegistryEntryDto existingEntry =
        new JobRegistryEntryDto(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
    existingEntry.setStatus(blockingStatus);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.of(existingEntry));

    // when
    final Throwable thrown =
        catchThrowable(() -> underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID));

    // then
    assertThat(thrown).isInstanceOf(OptimizeConflictException.class);
    verify(jobRegistryWriter, never()).createJobEntry(any(), any(), any());
  }

  @Test
  public void shouldQueueNewJobWhenExistingEntryHasFailedStatus() {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    final JobRegistryEntryDto failedEntry =
        new JobRegistryEntryDto(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
    failedEntry.setStatus(JobStatus.FAILED);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.of(failedEntry));

    // when
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID);

    // then
    verify(jobRegistryWriter)
        .createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
  }
}

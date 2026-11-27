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
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.Level;

public class ProcessDefinitionDeletionRequestServiceTest {

  private static final String PROCESS_DEFINITION_ID = "123456789";
  private static final String REQUESTED_BY = "test-client";

  private final ProcessDefinitionReader processDefinitionReader =
      mock(ProcessDefinitionReader.class);
  private final JobRegistryReader jobRegistryReader = mock(JobRegistryReader.class);
  private final JobRegistryWriter jobRegistryWriter = mock(JobRegistryWriter.class);

  @RegisterExtension
  private final LogCapturer logs =
      LogCapturer.create()
          .captureForType(ProcessDefinitionDeletionRequestService.class, Level.INFO);

  private final ProcessDefinitionDeletionRequestService underTest =
      new ProcessDefinitionDeletionRequestService(
          processDefinitionReader, jobRegistryReader, jobRegistryWriter);

  @BeforeEach
  void stubJobCreation() {
    when(jobRegistryWriter.createJobEntry(any(), any(), any()))
        .thenAnswer(
            invocation ->
                new JobRegistryEntryDto(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2)));
  }

  @Test
  public void shouldLogRequestingClientWhenRequestIsReceivedAndJobIsQueued() {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.empty());
    final JobRegistryEntryDto queuedJob =
        new JobRegistryEntryDto(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
    queuedJob.setId("queued-job-id");
    when(jobRegistryWriter.createJobEntry(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(queuedJob);

    // when
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY);

    // then: the request and the queued job are both attributable to the client, and the queued
    // line names the exact job so a later completion or failure log can be correlated with it
    assertThat(logs.getEvents()).hasSize(2);
    logs.assertContains(
        "Received request to delete Optimize data for process definition ["
            + PROCESS_DEFINITION_ID
            + "] from client ["
            + REQUESTED_BY
            + "]");
    logs.assertContains(
        "Queued deletion job [queued-job-id] for process definition ["
            + PROCESS_DEFINITION_ID
            + "] requested by client ["
            + REQUESTED_BY
            + "]");
  }

  @Test
  public void shouldQueueJobWhenIdIsValidAndNoBlockingEntryExists() {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.empty());

    // when
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY);

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
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY);
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY);

    // then: both requests are allowed to queue their own job; concurrent duplicate deletion
    // requests are accepted
    verify(jobRegistryWriter, times(2))
        .createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
  }

  @Test
  public void shouldRejectNonNumericId() {
    // when
    final Throwable thrown =
        catchThrowable(
            () -> underTest.queueProcessDefinitionDeletion("not-a-number", REQUESTED_BY));

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
        catchThrowable(
            () -> underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY));

    // then
    assertThat(thrown).isInstanceOf(NotFoundException.class);
    verify(jobRegistryWriter, never()).createJobEntry(any(), any(), any());
  }

  @Test
  public void shouldRejectWhenBlockingQueuedEntryExists() {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    final JobRegistryEntryDto existingEntry =
        new JobRegistryEntryDto(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
    existingEntry.setStatus(JobStatus.QUEUED);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.of(existingEntry));

    // when
    final Throwable thrown =
        catchThrowable(
            () -> underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY));

    // then
    assertThat(thrown).isInstanceOf(OptimizeConflictException.class);
    verify(jobRegistryWriter, never()).createJobEntry(any(), any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = JobStatus.class,
      names = {"COMPLETED", "FAILED"})
  public void shouldQueueNewJobWhenExistingEntryHasCompletedOrFailedStatus(
      final JobStatus nonBlockingStatus) {
    // given
    when(processDefinitionReader.processDefinitionExists(PROCESS_DEFINITION_ID)).thenReturn(true);
    final JobRegistryEntryDto existingEntry =
        new JobRegistryEntryDto(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
    existingEntry.setStatus(nonBlockingStatus);
    when(jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID))
        .thenReturn(Optional.of(existingEntry));

    // when
    underTest.queueProcessDefinitionDeletion(PROCESS_DEFINITION_ID, REQUESTED_BY);

    // then
    verify(jobRegistryWriter)
        .createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, PROCESS_DEFINITION_ID);
  }
}

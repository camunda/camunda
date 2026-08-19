/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JobDispatcherTest {

  private ConfigurationService configurationService;
  private JobRegistryReader jobRegistryReader;
  private JobRegistryWriter jobRegistryWriter;
  private final List<JobDispatcher> createdDispatchers = new ArrayList<>();

  @BeforeEach
  public void init() {
    configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();
    // Mockito mocks are not safe to invoke concurrently from multiple threads; pin the dispatcher
    // to a single worker thread so a batch's jobs are handled one at a time, keeping the mock
    // interactions in these tests deterministic.
    configurationService.getJobRegistryDispatcherConfiguration().setThreadCount(1);
    jobRegistryReader = mock(JobRegistryReader.class);
    jobRegistryWriter = mock(JobRegistryWriter.class);
  }

  @AfterEach
  public void shutdownDispatchers() {
    // dispatchNextBatch() lazily creates a non-daemon thread pool; without this, every test that
    // dispatches at least one job leaks its dispatcher's threads for the rest of the suite.
    createdDispatchers.forEach(JobDispatcher::destroy);
    createdDispatchers.clear();
  }

  @Test
  public void shouldDispatchQueuedJobToMatchingHandler() throws Exception {
    // given
    final JobHandler handler = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    final JobRegistryEntryDto job = jobEntry("job-1");
    when(jobRegistryReader.findOldestQueuedJobs(anyInt())).thenReturn(List.of(job));
    final JobDispatcher underTest = createDispatcherToTest(handler);

    // when
    underTest.dispatchNextBatch();

    // then
    verify(handler, times(1)).handle(job);
    verify(jobRegistryWriter).updateJobStatus("job-1", JobStatus.COMPLETED, null);
  }

  @Test
  public void shouldMarkJobAsFailedWhenHandlerThrows() throws Exception {
    // given
    final JobHandler handler = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    doThrow(new RuntimeException("boom")).when(handler).handle(any());
    final JobRegistryEntryDto job = jobEntry("job-1");
    when(jobRegistryReader.findOldestQueuedJobs(anyInt())).thenReturn(List.of(job));
    final JobDispatcher underTest = createDispatcherToTest(handler);

    // when
    underTest.dispatchNextBatch();

    // then
    verify(jobRegistryWriter)
        .updateJobStatus(eq("job-1"), eq(JobStatus.FAILED), eq("RuntimeException: boom"));
  }

  @Test
  public void shouldMarkJobAsFailedWhenNoHandlerRegisteredForEntityType() {
    // given
    final JobRegistryEntryDto job = jobEntry("job-1");
    when(jobRegistryReader.findOldestQueuedJobs(anyInt())).thenReturn(List.of(job));
    final JobDispatcher underTest = createDispatcherToTest();

    // when
    underTest.dispatchNextBatch();

    // then
    verify(jobRegistryWriter).updateJobStatus(eq("job-1"), eq(JobStatus.FAILED), any(String.class));
  }

  @Test
  public void shouldIsolateFailureOfOneJobFromOthersInSameBatch() throws Exception {
    // given
    final JobHandler handler = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    final JobRegistryEntryDto failingJob = jobEntry("job-failing", "entity-failing");
    final JobRegistryEntryDto succeedingJob = jobEntry("job-succeeding", "entity-succeeding");
    doThrow(new RuntimeException("boom")).when(handler).handle(failingJob);
    when(jobRegistryReader.findOldestQueuedJobs(anyInt()))
        .thenReturn(List.of(failingJob, succeedingJob));
    final JobDispatcher underTest = createDispatcherToTest(handler);

    // when
    underTest.dispatchNextBatch();

    // then
    verify(jobRegistryWriter).updateJobStatus(eq("job-failing"), eq(JobStatus.FAILED), any());
    verify(jobRegistryWriter)
        .updateJobStatus(eq("job-succeeding"), eq(JobStatus.COMPLETED), eq(null));
  }

  @Test
  public void shouldOnlyDispatchOldestEntryPerEntityInSameBatch() throws Exception {
    // given
    final JobHandler handler = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    final JobRegistryEntryDto olderEntry = jobEntry("job-older");
    final JobRegistryEntryDto newerDuplicateEntry = jobEntry("job-newer-duplicate");
    final JobRegistryEntryDto unrelatedEntry = jobEntry("job-unrelated", "entity-unrelated");
    when(jobRegistryReader.findOldestQueuedJobs(anyInt()))
        .thenReturn(List.of(olderEntry, newerDuplicateEntry, unrelatedEntry));
    final JobDispatcher underTest = createDispatcherToTest(handler);

    // when
    underTest.dispatchNextBatch();

    // then
    verify(handler, times(1)).handle(olderEntry);
    verify(handler, never()).handle(newerDuplicateEntry);
    verify(handler, times(1)).handle(unrelatedEntry);
    verify(jobRegistryWriter).updateJobStatus(eq("job-older"), eq(JobStatus.COMPLETED), eq(null));
    verify(jobRegistryWriter, never()).updateJobStatus(eq("job-newer-duplicate"), any(), any());
    verify(jobRegistryWriter)
        .updateJobStatus(eq("job-unrelated"), eq(JobStatus.COMPLETED), eq(null));
  }

  @Test
  public void shouldNotAbortBatchWhenRegistryWriteThrows() throws Exception {
    // given
    final JobHandler handler = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    final JobRegistryEntryDto jobWithFailingWrite =
        jobEntry("job-write-failure", "entity-write-failure");
    final JobRegistryEntryDto otherJob = jobEntry("job-other", "entity-other");
    doThrow(new RuntimeException("registry unavailable"))
        .when(jobRegistryWriter)
        .updateJobStatus(eq("job-write-failure"), any(), any());
    when(jobRegistryReader.findOldestQueuedJobs(anyInt()))
        .thenReturn(List.of(jobWithFailingWrite, otherJob));
    final JobDispatcher underTest = createDispatcherToTest(handler);

    // when / then
    assertThatCode(underTest::dispatchNextBatch).doesNotThrowAnyException();
    verify(handler, times(1)).handle(otherJob);
    verify(jobRegistryWriter).updateJobStatus(eq("job-other"), eq(JobStatus.COMPLETED), eq(null));
  }

  @Test
  public void shouldNotMarkJobAsFailedWhenHandlerSucceedsButRegistryWriteThrows() throws Exception {
    // given
    final JobHandler handler = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    final JobRegistryEntryDto job = jobEntry("job-1");
    doThrow(new RuntimeException("registry unavailable"))
        .when(jobRegistryWriter)
        .updateJobStatus(eq("job-1"), eq(JobStatus.COMPLETED), any());
    when(jobRegistryReader.findOldestQueuedJobs(anyInt())).thenReturn(List.of(job));
    final JobDispatcher underTest = createDispatcherToTest(handler);

    // when
    underTest.dispatchNextBatch();

    // then
    verify(handler, times(1)).handle(job);
    verify(jobRegistryWriter, never()).updateJobStatus(eq("job-1"), eq(JobStatus.FAILED), any());
  }

  @Test
  public void shouldNotQueryWriterWhenNoJobsAreQueued() {
    // given
    when(jobRegistryReader.findOldestQueuedJobs(anyInt())).thenReturn(List.of());
    final JobDispatcher underTest = createDispatcherToTest();

    // when
    underTest.dispatchNextBatch();

    // then
    verify(jobRegistryWriter, never()).updateJobStatus(any(), any(), any());
  }

  @Test
  public void shouldFailToConstructWhenTwoHandlersRegisterForSameJobTypeAndEntityType() {
    // given
    final JobHandler handler1 = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);
    final JobHandler handler2 = mockHandler(JobType.DELETE, EntityType.PROCESS_DEFINITION);

    // when / then
    assertThatExceptionOfType(OptimizeConfigurationException.class)
        .isThrownBy(() -> createDispatcherToTest(handler1, handler2));
  }

  @Test
  public void shouldFailToInitializeWhenThreadCountIsInvalid() {
    // given
    configurationService.getJobRegistryDispatcherConfiguration().setThreadCount(0);
    final JobDispatcher underTest = createDispatcherToTest();

    // when / then
    assertThatExceptionOfType(OptimizeConfigurationException.class).isThrownBy(underTest::init);
  }

  @Test
  public void shouldStartSchedulingWhenEnabled() {
    // given
    configurationService.getJobRegistryDispatcherConfiguration().setEnabled(true);
    final JobDispatcher underTest = createDispatcherToTest();

    // when
    underTest.init();

    // then
    try {
      assertThat(underTest.isScheduledToRun()).isTrue();
    } finally {
      underTest.destroy();
    }
  }

  @Test
  public void shouldNotStartSchedulingWhenDisabled() {
    // given
    configurationService.getJobRegistryDispatcherConfiguration().setEnabled(false);
    final JobDispatcher underTest = createDispatcherToTest();

    // when
    underTest.init();

    // then
    assertThat(underTest.isScheduledToRun()).isFalse();
  }

  private JobHandler mockHandler(final JobType jobType, final EntityType entityType) {
    final JobHandler handler = mock(JobHandler.class);
    when(handler.getJobType()).thenReturn(jobType);
    when(handler.getEntityType()).thenReturn(entityType);
    return handler;
  }

  private JobRegistryEntryDto jobEntry(final String id) {
    return jobEntry(id, "entity-1");
  }

  private JobRegistryEntryDto jobEntry(final String id, final String entityId) {
    final JobRegistryEntryDto job =
        new JobRegistryEntryDto(JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);
    job.setId(id);
    return job;
  }

  private JobDispatcher createDispatcherToTest(final JobHandler... jobHandlers) {
    final JobDispatcher dispatcher =
        new JobDispatcher(
            configurationService, jobRegistryReader, jobRegistryWriter, List.of(jobHandlers));
    createdDispatchers.add(dispatcher);
    return dispatcher;
  }
}

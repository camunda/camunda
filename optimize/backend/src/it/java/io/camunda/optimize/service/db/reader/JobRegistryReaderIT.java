/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.optimize.query.job.TargetEntityType;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JobRegistryReaderIT extends AbstractBrokerlessZeebeCCSMIT {

  private JobRegistryReader jobRegistryReader;
  private JobRegistryWriter jobRegistryWriter;

  @BeforeEach
  void setup() {
    jobRegistryReader = embeddedOptimizeExtension.getBean(JobRegistryReader.class);
    jobRegistryWriter = embeddedOptimizeExtension.getBean(JobRegistryWriter.class);
  }

  @AfterEach
  void resetClock() {
    LocalDateUtil.reset();
  }

  @Test
  void shouldFindOnlyQueuedJob() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2251799813685251");

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

    // then
    assertThat(found)
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getId()).isEqualTo(created.getId());
              assertThat(entry.getStatus()).isEqualTo(JobStatus.QUEUED);
            });
  }

  @Test
  void shouldNotFindQueuedJobsWhenNoneAreQueued() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

    // then
    assertThat(found).isEmpty();
  }

  @Test
  void shouldNotFindQueuedJobsWhenIndexIsEmpty() {
    // given no job entries at all

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

    // then
    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnQueuedJobsOldestFirstWhenMultipleAreQueued() {
    // given
    // Pin the clock so the two entries get distinct, ordered createdAt values -- otherwise both
    // could land in the same millisecond and the sort-by-oldest assertion would be flaky.
    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
    final JobRegistryEntryDto olderEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
    final JobRegistryEntryDto newerEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

    // then
    assertThat(found)
        .extracting(JobRegistryEntryDto::getId)
        .containsExactly(olderEntry.getId(), newerEntry.getId());
  }

  @Test
  void shouldRespectLimitWhenMultipleAreQueued() {
    // given
    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
    final JobRegistryEntryDto olderEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
    jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(1);

    // then
    assertThat(found)
        .singleElement()
        .satisfies(entry -> assertThat(entry.getId()).isEqualTo(olderEntry.getId()));
  }

  @Test
  void shouldSkipCompletedEntryAndReturnTheQueuedOne() {
    // given
    final JobRegistryEntryDto completedEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(completedEntry.getId(), JobStatus.COMPLETED, null);

    final JobRegistryEntryDto queuedEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

    // then
    assertThat(found)
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getId()).isEqualTo(queuedEntry.getId());
              assertThat(entry.getStatus()).isEqualTo(JobStatus.QUEUED);
            });
  }

  @Test
  void shouldSkipFailedEntryAndReturnTheQueuedOne() {
    // given
    final JobRegistryEntryDto failedEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(failedEntry.getId(), JobStatus.FAILED, "boom");

    final JobRegistryEntryDto queuedEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    // when
    final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

    // then
    assertThat(found)
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getId()).isEqualTo(queuedEntry.getId());
              assertThat(entry.getStatus()).isEqualTo(JobStatus.QUEUED);
            });
  }

  @Test
  void shouldFindLastJobByJobTypeAndTargetEntityId() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2251799813685251");

    // when
    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findLastByJobTypeAndTargetEntityId(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2251799813685251");

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(created.getId());
  }

  @Test
  void shouldFindLastJobByJobTypeAndTargetEntityIdRegardlessOfStatus() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    // when
    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findLastByJobTypeAndTargetEntityId(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(JobStatus.COMPLETED);
  }

  @Test
  void shouldNotFindJobForUnknownTargetEntityId() {
    // given
    jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    // when
    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findLastByJobTypeAndTargetEntityId(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "does-not-exist");

    // then
    assertThat(found).isEmpty();
  }
}

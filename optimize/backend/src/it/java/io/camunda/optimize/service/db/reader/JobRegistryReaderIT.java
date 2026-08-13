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
  void shouldFindOldestQueuedJob() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE,
            TargetEntityType.PROCESS_DEFINITION,
            "2251799813685251");

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findOldestQueuedJob(JobType.PROCESS_DEFINITION_DATA_DELETE);

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(created.getId());
    assertThat(found.get().getStatus()).isEqualTo(JobStatus.QUEUED);
  }

  @Test
  void shouldNotFindQueuedJobWhenNoneAreQueued() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findOldestQueuedJob(JobType.PROCESS_DEFINITION_DATA_DELETE);

    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnOldestQueuedJobWhenMultipleAreQueued() {
    // Pin the clock so the two entries get distinct, ordered createdAt values -- otherwise both
    // could land in the same millisecond and the sort-by-oldest assertion would be flaky.
    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
    final JobRegistryEntryDto olderEntry =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
    jobRegistryWriter.createJobEntry(
        JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findOldestQueuedJob(JobType.PROCESS_DEFINITION_DATA_DELETE);

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(olderEntry.getId());
    assertThat(found.get().getTargetEntityId()).isEqualTo("1");
  }

  @Test
  void shouldSkipCompletedEntryAndReturnTheQueuedOne() {
    final JobRegistryEntryDto completedEntry =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(completedEntry.getId(), JobStatus.COMPLETED, null);

    final JobRegistryEntryDto queuedEntry =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findOldestQueuedJob(JobType.PROCESS_DEFINITION_DATA_DELETE);

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(queuedEntry.getId());
    assertThat(found.get().getStatus()).isEqualTo(JobStatus.QUEUED);
  }

  @Test
  void shouldFindJobByJobTypeAndTargetEntityId() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE,
            TargetEntityType.PROCESS_DEFINITION,
            "2251799813685251");

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findByJobTypeAndTargetEntityId(
            JobType.PROCESS_DEFINITION_DATA_DELETE, "2251799813685251");

    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(created.getId());
  }

  @Test
  void shouldFindJobByJobTypeAndTargetEntityIdRegardlessOfStatus() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findByJobTypeAndTargetEntityId(
            JobType.PROCESS_DEFINITION_DATA_DELETE, "1");

    assertThat(found).isPresent();
    assertThat(found.get().getStatus()).isEqualTo(JobStatus.COMPLETED);
  }

  @Test
  void shouldNotFindJobForUnknownTargetEntityId() {
    jobRegistryWriter.createJobEntry(
        JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findByJobTypeAndTargetEntityId(
            JobType.PROCESS_DEFINITION_DATA_DELETE, "does-not-exist");

    assertThat(found).isEmpty();
  }
}

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
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

  @Nested
  class FindOldestQueuedJobs {

    @Test
    void shouldFindOnlyQueuedJob() {
      // given
      final JobRegistryEntryDto created =
          jobRegistryWriter.createJobEntry(
              JobType.DELETE, EntityType.PROCESS_DEFINITION, "2251799813685251");

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
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
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
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");

      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
      final JobRegistryEntryDto newerEntry =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

      // when
      final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

      // then
      assertThat(found)
          .extracting(JobRegistryEntryDto::getId)
          .containsExactly(olderEntry.getId(), newerEntry.getId());
    }

    @Test
    void shouldBreakTiesByIdWhenCreatedAtIsIdentical() {
      // given
      // Same pinned instant for both entries -- createdAt alone can't order two entries created
      // within the same millisecond, so this exercises the id tiebreaker.
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
      final JobRegistryEntryDto first =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "300");
      final JobRegistryEntryDto second =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "100");
      final JobRegistryEntryDto third =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "200");
      final List<String> expectedOrder =
          Stream.of(second.getId(), third.getId(), first.getId()).sorted().toList();

      // when
      final List<JobRegistryEntryDto> found = jobRegistryReader.findOldestQueuedJobs(10);

      // then
      assertThat(found)
          .extracting(JobRegistryEntryDto::getId)
          .containsExactlyElementsOf(expectedOrder);
    }

    @Test
    void shouldRespectLimitWhenMultipleAreQueued() {
      // given
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
      final JobRegistryEntryDto olderEntry =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");

      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

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
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      jobRegistryWriter.updateJobStatus(completedEntry.getId(), JobStatus.COMPLETED, null);

      final JobRegistryEntryDto queuedEntry =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

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
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      jobRegistryWriter.updateJobStatus(failedEntry.getId(), JobStatus.FAILED, "boom");

      final JobRegistryEntryDto queuedEntry =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

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
  }

  @Nested
  class FindLastByJobTypeAndEntityId {

    @Test
    void shouldFindLastJobByJobTypeAndEntityId() {
      final String entityId = "2251799813685251";
      // given
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);

      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:02.000+00:00"));
      final JobRegistryEntryDto lastEntry =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);

      // not the last but persisted after the last one
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);

      // when
      final Optional<JobRegistryEntryDto> found =
          jobRegistryReader.findLastByJobTypeAndEntityId(
              JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);

      // then
      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(lastEntry.getId());
    }

    @Test
    void shouldFindLastJobByJobTypeAndEntityIdRegardlessOfStatus() {
      // given
      final JobRegistryEntryDto created =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

      // when
      final Optional<JobRegistryEntryDto> found =
          jobRegistryReader.findLastByJobTypeAndEntityId(
              JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");

      // then
      assertThat(found).isPresent();
      assertThat(found.get().getStatus()).isEqualTo(JobStatus.COMPLETED);
    }

    @Test
    void shouldNotFindJobForUnknownEntityId() {
      // given
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");

      // when
      final Optional<JobRegistryEntryDto> found =
          jobRegistryReader.findLastByJobTypeAndEntityId(
              JobType.DELETE, EntityType.PROCESS_DEFINITION, "does-not-exist");

      // then
      assertThat(found).isEmpty();
    }
  }

  @Nested
  class FindNewestEntityIds {

    @Test
    void shouldFindAllEntries() {
      // given
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

      // when
      final List<String> found =
          jobRegistryReader.findNewestEntityIds(JobType.DELETE, EntityType.PROCESS_DEFINITION, 10);

      // then
      assertThat(found).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void shouldCapResultsAtLimitKeepingNewestFirst() {
      // given
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:02.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "3");

      // when
      final List<String> found =
          jobRegistryReader.findNewestEntityIds(JobType.DELETE, EntityType.PROCESS_DEFINITION, 2);

      // then
      assertThat(found).containsExactly("3", "2");
    }

    @Test
    void shouldNotReturnDuplicateEntriesWhenOneEntityHasMultipleRegistryEntries() {
      // given a retried deletion leaves two job registry documents for the same entityId.
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
      final JobRegistryEntryDto failedEntry =
          jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      jobRegistryWriter.updateJobStatus(failedEntry.getId(), JobStatus.FAILED, "boom");
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
      LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:02.000+00:00"));
      jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

      // when
      final List<String> found =
          jobRegistryReader.findNewestEntityIds(JobType.DELETE, EntityType.PROCESS_DEFINITION, 2);

      // then collapsing must keep exactly one entry per entityId
      assertThat(found).containsExactlyInAnyOrder("1", "2");
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JobDispatcherIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final int BATCH_SIZE = 10;
  private JobDispatcher jobDispatcher;
  private JobRegistryReader jobRegistryReader;
  private JobRegistryWriter jobRegistryWriter;

  @BeforeEach
  void setup() {
    jobDispatcher = embeddedOptimizeExtension.getBean(JobDispatcher.class);
    jobRegistryReader = embeddedOptimizeExtension.getBean(JobRegistryReader.class);
    jobRegistryWriter = embeddedOptimizeExtension.getBean(JobRegistryWriter.class);

    embeddedOptimizeExtension
        .getConfigurationService()
        .getJobRegistryDispatcherConfiguration()
        .setBatchSize(BATCH_SIZE);
  }

  @AfterEach
  void resetClock() {
    LocalDateUtil.reset();
  }

  @Test
  void shouldMarkQueuedJobAsFailedWhenNoHandlerIsRegistered() {
    // given
    // No JobHandler bean exists yet for (DELETE, PROCESS_DEFINITION) in this test context -- the
    // concrete handler is separate follow-up work, so this exercises the dispatcher's "unhandled
    // job type" path against the real index.
    final JobRegistryEntryDto queued =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, "2251799813685251");

    // when
    jobDispatcher.dispatchNextBatch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final Optional<JobRegistryEntryDto> found =
        jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, "2251799813685251");
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(queued.getId());
    assertThat(found.get().getStatus()).isEqualTo(JobStatus.FAILED);
  }

  @Test
  void shouldLeaveQueueEmptyAfterDispatchingAllQueuedJobs() {
    // given
    jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "1");
    jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, "2");

    // when
    jobDispatcher.dispatchNextBatch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    final List<JobRegistryEntryDto> stillQueued =
        jobRegistryReader.findOldestQueuedJobs(BATCH_SIZE);
    assertThat(stillQueued).isEmpty();
  }

  @Test
  void shouldOnlyDispatchOldestEntryPerEntityInSameBatch() {
    // given
    final String entityId = "2251799813685252";

    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:00.000+00:00"));
    final JobRegistryEntryDto olderEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);

    LocalDateUtil.setCurrentTime(OffsetDateTime.parse("2026-01-01T00:00:01.000+00:00"));
    final JobRegistryEntryDto newerDuplicateEntry =
        jobRegistryWriter.createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);

    // when
    jobDispatcher.dispatchNextBatch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    // only the older entry was dispatched (and failed, since no handler is registered); the
    // newer duplicate for the same entity was left untouched for a later batch
    final Optional<JobRegistryEntryDto> dispatchedEntry =
        jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, entityId);
    assertThat(dispatchedEntry).isPresent();
    assertThat(dispatchedEntry.get().getId()).isEqualTo(newerDuplicateEntry.getId());
    assertThat(dispatchedEntry.get().getStatus()).isEqualTo(JobStatus.QUEUED);

    final List<JobRegistryEntryDto> stillQueued =
        jobRegistryReader.findOldestQueuedJobs(BATCH_SIZE);
    assertThat(stillQueued)
        .singleElement()
        .satisfies(entry -> assertThat(entry.getId()).isEqualTo(newerDuplicateEntry.getId()));
  }
}

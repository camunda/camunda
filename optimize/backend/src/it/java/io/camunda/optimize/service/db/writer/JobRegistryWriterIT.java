/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.JOB_REGISTRY_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.optimize.query.job.TargetEntityType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JobRegistryWriterIT extends AbstractBrokerlessZeebeCCSMIT {

  private JobRegistryWriter jobRegistryWriter;

  @BeforeEach
  void setup() {
    jobRegistryWriter = embeddedOptimizeExtension.getBean(JobRegistryWriter.class);
  }

  @Test
  void shouldCreateQueuedJobEntry() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2251799813685251");

    // when
    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);

    // then
    assertThat(stored)
        .singleElement()
        .satisfies(
            jobRegistry -> {
              assertThat(jobRegistry.getId()).isEqualTo(created.getId());
              assertThat(jobRegistry.getJobType()).isEqualTo(JobType.DELETE);
              assertThat(jobRegistry.getTargetEntityType())
                  .isEqualTo(TargetEntityType.PROCESS_DEFINITION);
              assertThat(jobRegistry.getTargetEntityId()).isEqualTo("2251799813685251");
              assertThat(jobRegistry.getStatus()).isEqualTo(JobStatus.QUEUED);
              assertThat(jobRegistry.getUpdatedAt()).isNull();
            });
  }

  @Test
  void shouldUpdateJobStatusToCompletedWithCompletionTimestamp() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    // when
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    // then
    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(stored)
        .singleElement()
        .satisfies(
            jobRegistry -> {
              assertThat(jobRegistry.getStatus()).isEqualTo(JobStatus.COMPLETED);
              assertThat(jobRegistry.getUpdatedAt()).isNotNull();
              assertThat(jobRegistry.getErrorMessage()).isNull();
            });
  }

  @Test
  void shouldUpdateJobStatusToFailedWithErrorMessage() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    // when
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.FAILED, "boom");

    // then
    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(stored)
        .singleElement()
        .satisfies(
            jobRegistry -> {
              assertThat(jobRegistry.getStatus()).isEqualTo(JobStatus.FAILED);
              assertThat(jobRegistry.getErrorMessage()).isEqualTo("boom");
              assertThat(jobRegistry.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  void shouldClearErrorMessageWhenRetryCompletesSuccessfully() {
    // given
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    // Move the entry into a FAILED state first, so errorMessage is populated. A retry that
    // completes successfully must clear it back to null -- the JobRegistryEntryUpdateDto relies
    // on nulls actually serializing for that partial-doc update to work, and a test starting
    // straight from QUEUED can't catch a regression there.
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.FAILED, "boom");
    final List<JobRegistryEntryDto> afterFailure =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(afterFailure)
        .singleElement()
        .satisfies(e -> assertThat(e.getErrorMessage()).isNotNull());

    // when
    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    // then
    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(stored)
        .singleElement()
        .satisfies(
            jobRegistry -> {
              assertThat(jobRegistry.getStatus()).isEqualTo(JobStatus.COMPLETED);
              assertThat(jobRegistry.getUpdatedAt()).isNotNull();
              assertThat(jobRegistry.getErrorMessage()).isNull();
            });
  }
}

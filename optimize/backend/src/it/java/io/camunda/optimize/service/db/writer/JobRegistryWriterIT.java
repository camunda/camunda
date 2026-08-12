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
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE,
            TargetEntityType.PROCESS_DEFINITION,
            "2251799813685251");

    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);

    assertThat(stored).hasSize(1);
    final JobRegistryEntryDto persisted = stored.get(0);
    assertThat(persisted.getId()).isEqualTo(created.getId());
    assertThat(persisted.getJobType()).isEqualTo(JobType.PROCESS_DEFINITION_DATA_DELETE);
    assertThat(persisted.getTargetEntityType()).isEqualTo(TargetEntityType.PROCESS_DEFINITION);
    assertThat(persisted.getTargetEntityId()).isEqualTo("2251799813685251");
    assertThat(persisted.getStatus()).isEqualTo(JobStatus.QUEUED);
    assertThat(persisted.getCompletedAt()).isNull();
  }

  @Test
  void shouldUpdateJobStatusToCompletedWithCompletionTimestamp() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.COMPLETED, null);

    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).getStatus()).isEqualTo(JobStatus.COMPLETED);
    assertThat(stored.get(0).getCompletedAt()).isNotNull();
    assertThat(stored.get(0).getErrorMessage()).isNull();
  }

  @Test
  void shouldUpdateJobStatusToFailedWithErrorMessage() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.FAILED, "boom");

    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(stored.get(0).getErrorMessage()).isEqualTo("boom");
    assertThat(stored.get(0).getCompletedAt()).isNotNull();
  }

  @Test
  void shouldUpdateJobStatusToRunningWithoutCompletionTimestamp() {
    final JobRegistryEntryDto created =
        jobRegistryWriter.createJobEntry(
            JobType.PROCESS_DEFINITION_DATA_DELETE, TargetEntityType.PROCESS_DEFINITION, "1");

    jobRegistryWriter.updateJobStatus(created.getId(), JobStatus.RUNNING, null);

    final List<JobRegistryEntryDto> stored =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            JOB_REGISTRY_INDEX_NAME, JobRegistryEntryDto.class);
    assertThat(stored).hasSize(1);
    assertThat(stored.get(0).getStatus()).isEqualTo(JobStatus.RUNNING);
    assertThat(stored.get(0).getCompletedAt()).isNull();
  }
}

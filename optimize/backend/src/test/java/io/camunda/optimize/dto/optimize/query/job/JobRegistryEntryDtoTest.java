/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobRegistryEntryDtoTest {

  @Test
  void shouldInitializeQueuedEntryWithGeneratedIdAndCreationTimestamp() {
    final JobRegistryEntryDto entry =
        new JobRegistryEntryDto(
            JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2251799813685251");

    assertThat(entry.getId()).isNotBlank();
    assertThat(entry.getJobType()).isEqualTo(JobType.DELETE);
    assertThat(entry.getTargetEntityType()).isEqualTo(TargetEntityType.PROCESS_DEFINITION);
    assertThat(entry.getTargetEntityId()).isEqualTo("2251799813685251");
    assertThat(entry.getStatus()).isEqualTo(JobStatus.QUEUED);
    assertThat(entry.getErrorMessage()).isNull();
    assertThat(entry.getCreatedAt()).isNotNull();
    assertThat(entry.getUpdatedAt()).isNull();
  }

  @Test
  void shouldGenerateDifferentIdsForEachEntry() {
    final JobRegistryEntryDto first =
        new JobRegistryEntryDto(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "1");
    final JobRegistryEntryDto second =
        new JobRegistryEntryDto(JobType.DELETE, TargetEntityType.PROCESS_DEFINITION, "2");

    assertThat(first.getId()).isNotEqualTo(second.getId());
  }
}

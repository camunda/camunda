/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel.Builder;
import io.camunda.search.entities.JobEntity.JobState;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ListParameterUpsertMergerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void shouldMergeWithItemInBatchList() {
    // Given a batch insert with 3 jobs
    final var job1 = createJob(100L, JobState.CREATED, null);
    final var job2 = createJob(101L, JobState.CREATED, null);
    final var job3 = createJob(102L, JobState.CREATED, null);

    final var batchDto = new BatchInsertDto<>(job1, job2, job3);

    final var queueItem =
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.INSERT,
            101L,
            "io.camunda.db.rdbms.sql.JobMapper.insert",
            batchDto);

    // When we try to merge an update for jobKey=101
    final var merger =
        new ListParameterUpsertMerger<JobDbModel>(
            ContextType.JOB,
            101L,
            JobDbModel::jobKey,
            b -> ((Builder) b).state(JobState.COMPLETED).endTime(NOW));

    // Then it should be able to merge
    assertThat(merger.canBeMerged(queueItem)).isTrue();

    // And when we perform the merge
    final var mergedItem = merger.merge(queueItem);

    // Then the batch should contain the updated item
    final var mergedDto = (BatchInsertDto<JobDbModel>) mergedItem.parameter();
    assertThat(mergedDto.dbModels()).hasSize(3);

    // The first and third items should be unchanged
    assertThat(mergedDto.dbModels().get(0).jobKey()).isEqualTo(100L);
    assertThat(mergedDto.dbModels().get(0).state()).isEqualTo(JobState.CREATED);
    assertThat(mergedDto.dbModels().get(0).endTime()).isNull();

    assertThat(mergedDto.dbModels().get(2).jobKey()).isEqualTo(102L);
    assertThat(mergedDto.dbModels().get(2).state()).isEqualTo(JobState.CREATED);
    assertThat(mergedDto.dbModels().get(2).endTime()).isNull();

    // The second item should be updated
    assertThat(mergedDto.dbModels().get(1).jobKey()).isEqualTo(101L);
    assertThat(mergedDto.dbModels().get(1).state()).isEqualTo(JobState.COMPLETED);
    assertThat(mergedDto.dbModels().get(1).endTime()).isEqualTo(NOW);
  }

  @Test
  void shouldNotMergeWhenKeyNotInBatchList() {
    // Given a batch insert with 2 jobs
    final var job1 = createJob(100L, JobState.CREATED, null);
    final var job2 = createJob(102L, JobState.CREATED, null);

    final var batchDto = new BatchInsertDto<>(job1, job2);

    final var queueItem =
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.INSERT,
            100L,
            "io.camunda.db.rdbms.sql.JobMapper.insert",
            batchDto);

    // When we try to merge an update for jobKey=101 (not in the batch)
    final var merger =
        new ListParameterUpsertMerger<JobDbModel>(
            ContextType.JOB, 101L, JobDbModel::jobKey, b -> b);

    // Then it should not be able to merge
    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  @Test
  void shouldNotMergeWhenContextTypeDifferent() {
    // Given a batch insert with the JOB context
    final var job = createJob(100L, JobState.CREATED, null);

    final var batchDto = new BatchInsertDto<>(job);

    final var queueItem =
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.INSERT,
            100L,
            "io.camunda.db.rdbms.sql.JobMapper.insert",
            batchDto);

    // When we try to merge with a different context type
    final var merger =
        new ListParameterUpsertMerger<JobDbModel>(
            ContextType.PROCESS_INSTANCE, // Different context type
            100L,
            JobDbModel::jobKey,
            b -> b);

    // Then it should not be able to merge
    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  @Test
  void shouldNotMergeWhenParameterIsNotBatchInsertDto() {
    // Given a queue item with a single JobDbModel (not a batch)
    final var job = createJob(100L, JobState.CREATED, null);

    final var queueItem =
        new QueueItem(
            ContextType.JOB,
            WriteStatementType.UPDATE,
            100L,
            "io.camunda.db.rdbms.sql.JobMapper.update",
            job); // Single model, not BatchInsertDto

    // When we try to merge
    final var merger =
        new ListParameterUpsertMerger<JobDbModel>(
            ContextType.JOB, 100L, JobDbModel::jobKey, b -> b);

    // Then it should not be able to merge
    assertThat(merger.canBeMerged(queueItem)).isFalse();
  }

  private JobDbModel createJob(
      final Long jobKey, final JobState state, final OffsetDateTime endTime) {
    return new JobDbModel.Builder()
        .jobKey(jobKey)
        .state(state)
        .endTime(endTime)
        .type("test-type")
        .retries(3)
        .partitionId(1)
        .build();
  }
}

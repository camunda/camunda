/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper.EndProcessInstanceDto;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper.ProcessInstanceTagsDto;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.UpsertMerger;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessInstanceWriterTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ProcessInstanceMapper mapper;
  private ExecutionQueue executionQueue;
  private ProcessInstanceWriter writer;

  @BeforeEach
  void setUp() {
    mapper = mock(ProcessInstanceMapper.class);
    executionQueue = mock(ExecutionQueue.class);
    writer = new ProcessInstanceWriter(mapper, executionQueue);
  }

  @Test
  void whenFinishProcessCanBeMergedWithInsertNoItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(true);

    writer.finish(1L, ProcessInstanceState.COMPLETED, NOW);

    verify(executionQueue, never()).executeInQueue(any(QueueItem.class));
  }

  @Test
  void whenFinishProcessCannotBeMergedWithInsertItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(UpsertMerger.class))).thenReturn(false);

    writer.finish(1L, ProcessInstanceState.COMPLETED, NOW);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.PROCESS_INSTANCE,
                    WriteStatementType.UPDATE,
                    1L,
                    "io.camunda.db.rdbms.sql.ProcessInstanceMapper.updateStateAndEndDate",
                    new EndProcessInstanceDto(1L, ProcessInstanceState.COMPLETED, NOW))));
  }

  @Test
  void shouldUseTagsDtoForInsertTagsQueueItemWhenCreatedWithTags() {
    // given
    final var processInstance =
        new ProcessInstanceDbModelBuilder()
            .processInstanceKey(1L)
            .rootProcessInstanceKey(1L)
            .processDefinitionKey(1L)
            .processDefinitionId("process")
            .state(ProcessInstanceState.ACTIVE)
            .startDate(NOW)
            .tenantId("<default>")
            .version(1)
            .partitionId(1)
            .treePath("PI_1")
            .tags(Set.of("myTag"))
            .build();

    // when
    writer.create(processInstance);

    // then - two queue items are created: one for insert, one for insertTags
    final var captor = ArgumentCaptor.forClass(QueueItem.class);
    verify(executionQueue, times(2)).executeInQueue(captor.capture());

    final var insertTagsItem =
        captor.getAllValues().stream()
            .filter(i -> i.statementId().endsWith("insertTags"))
            .findFirst()
            .orElseThrow();

    // the insertTags queue item must use ProcessInstanceTagsDto, NOT ProcessInstanceDbModel,
    // so that the UpsertMerger for ProcessInstanceDbModel does not match it when finish() is called
    assertThat(insertTagsItem.parameter()).isInstanceOf(ProcessInstanceTagsDto.class);
    assertThat(insertTagsItem.parameter()).isNotInstanceOf(ProcessInstanceDbModel.class);

    final var tagsDto = (ProcessInstanceTagsDto) insertTagsItem.parameter();
    assertThat(tagsDto.processInstanceKey()).isEqualTo(1L);
    assertThat(tagsDto.tags()).containsExactlyInAnyOrder("myTag");
  }

  @Test
  void shouldNotMatchInsertTagsItemWithUpsertMergerWhenFinishingProcessWithTags() {
    // given - the insertTags item uses ProcessInstanceTagsDto (not ProcessInstanceDbModel)
    final long key = 1L;
    final var insertTagsItem =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            key,
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insertTags",
            new ProcessInstanceTagsDto(key, Set.of("myTag")));

    final var insertItem =
        new QueueItem(
            ContextType.PROCESS_INSTANCE,
            WriteStatementType.INSERT,
            key,
            "io.camunda.db.rdbms.sql.ProcessInstanceMapper.insert",
            new ProcessInstanceDbModelBuilder()
                .processInstanceKey(key)
                .rootProcessInstanceKey(key)
                .processDefinitionKey(1L)
                .processDefinitionId("process")
                .state(ProcessInstanceState.ACTIVE)
                .startDate(NOW)
                .tenantId("<default>")
                .version(1)
                .partitionId(1)
                .treePath("PI_1")
                .tags(Set.of("myTag"))
                .build());

    final var merger =
        new UpsertMerger<>(
            ContextType.PROCESS_INSTANCE,
            key,
            ProcessInstanceDbModel.class,
            (ProcessInstanceDbModelBuilder b) ->
                b.state(ProcessInstanceState.COMPLETED).endDate(NOW));

    // the insertTags item must NOT be matched by the UpsertMerger for ProcessInstanceDbModel
    assertThat(merger.canBeMerged(insertTagsItem)).isFalse();
    // the insert item must be matched
    assertThat(merger.canBeMerged(insertItem)).isTrue();
  }
}

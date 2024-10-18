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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.EndFlowNodeDto;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.service.FlowNodeInstanceWriter.EndFlowNodeToInsertMerger;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FlowNodeInstanceWriterTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  private ExecutionQueue executionQueue;
  private FlowNodeInstanceWriter writer;

  @BeforeEach
  void setUp() {
    executionQueue = mock(ExecutionQueue.class);
    writer = new FlowNodeInstanceWriter(executionQueue);
  }

  @Test
  void whenEndFlowNodeCanBeMergedWithInsertNoItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(EndFlowNodeToInsertMerger.class)))
        .thenReturn(true);

    writer.end(1L, FlowNodeState.COMPLETED, NOW);

    verify(executionQueue, never()).executeInQueue(any(QueueItem.class));
  }

  @Test
  void whenEndFlowNodeCannotBeMergedWithInsertItemShouldBeEnqueued() {
    when(executionQueue.tryMergeWithExistingQueueItem(any(EndFlowNodeToInsertMerger.class)))
        .thenReturn(false);

    writer.end(1L, FlowNodeState.COMPLETED, NOW);

    verify(executionQueue)
        .executeInQueue(
            eq(
                new QueueItem(
                    ContextType.FLOW_NODE,
                    1L,
                    "io.camunda.db.rdbms.sql.FlowNodeInstanceMapper.updateStateAndEndDate",
                    new FlowNodeInstanceMapper.EndFlowNodeDto(1L, FlowNodeState.COMPLETED, NOW))));
  }

  @Test
  void shouldOnlyUpdateStatusAndEndDate() {
    final var combiner =
        new EndFlowNodeToInsertMerger(new EndFlowNodeDto(1L, FlowNodeState.COMPLETED, NOW));

    final var insertParameter =
        createRandomized(
            b -> b.flowNodeInstanceKey(1L).endDate(NOW.minusDays(1)).state(FlowNodeState.ACTIVE));
    final var queueItem = new QueueItem(ContextType.FLOW_NODE, 1L, "statement", insertParameter);
    final var newQueueItem = combiner.merge(queueItem);

    assertThat(queueItem)
        .usingRecursiveComparison()
        .ignoringFields("parameter")
        .isEqualTo(newQueueItem);

    assertThat(queueItem.parameter())
        .usingRecursiveComparison()
        .ignoringFields("state", "endDate")
        .isEqualTo(newQueueItem.parameter());

    final var newParameter = ((FlowNodeInstanceDbModel) newQueueItem.parameter());
    assertThat(newParameter.state()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(newParameter.endDate()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @MethodSource
  void statusUpdateToInsertCombinerCanCombine(final QueueItem item, final boolean result) {
    final var combiner =
        new EndFlowNodeToInsertMerger(new EndFlowNodeDto(1L, FlowNodeState.COMPLETED, NOW));

    assertThat(combiner.canBeMerged(item)).isEqualTo(result);
  }

  static Stream<Arguments> statusUpdateToInsertCombinerCanCombine() {
    return Stream.of(
        Arguments.of(
            new QueueItem(
                ContextType.FLOW_NODE, 1L, "statement1", mock(FlowNodeInstanceDbModel.class)),
            true),
        Arguments.of(new QueueItem(ContextType.FLOW_NODE, 1L, "statement1", "bla"), false),
        Arguments.of(new QueueItem(ContextType.FLOW_NODE, 1L, "statement1", null), false),
        Arguments.of(new QueueItem(ContextType.FLOW_NODE, 2L, "statement1", null), false),
        Arguments.of(new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", null), false));
  }

  public static FlowNodeInstanceDbModel createRandomized(
      final Function<FlowNodeInstanceDbModelBuilder, FlowNodeInstanceDbModelBuilder>
          builderFunction) {
    final var random = new Random();
    final var builder =
        new FlowNodeInstanceDbModelBuilder()
            .flowNodeInstanceKey(random.nextLong())
            .processInstanceKey(random.nextLong())
            .processDefinitionKey(random.nextLong())
            .processDefinitionId("process-" + random.nextInt(10000))
            .flowNodeId("flowNode-" + random.nextInt(10000))
            .startDate(NOW.plus(random.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(random.nextInt(), ChronoUnit.MILLIS))
            .treePath(UUID.randomUUID().toString())
            .incidentKey(random.nextLong())
            .scopeKey(random.nextLong())
            .state(FlowNodeState.COMPLETED)
            .type(FlowNodeType.SERVICE_TASK)
            .tenantId("tenant-" + random.nextInt(10000));

    return builderFunction.apply(builder).build();
  }
}

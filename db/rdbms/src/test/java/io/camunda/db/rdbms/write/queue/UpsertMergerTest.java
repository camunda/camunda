/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UpsertMergerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void shouldOnlyUpdateSpecificFields() {
    final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder> mergerFunction =
        b -> b.state(ProcessInstanceState.COMPLETED).endDate(NOW);
    final var merger =
        new UpsertMerger<>(
            ContextType.PROCESS_INSTANCE, 1L, ProcessInstanceDbModel.class, mergerFunction);

    final var insertParameter =
        createRandomized(
            b ->
                b.processInstanceKey(1L)
                    .endDate(NOW.minusDays(1))
                    .state(ProcessInstanceState.ACTIVE));
    final var queueItem =
        new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement", insertParameter);
    final var newQueueItem = merger.merge(queueItem);

    assertThat(queueItem)
        .usingRecursiveComparison()
        .ignoringFields("parameter")
        .isEqualTo(newQueueItem);

    assertThat(queueItem.parameter())
        .usingRecursiveComparison()
        .ignoringFields("state", "endDate")
        .isEqualTo(newQueueItem.parameter());

    final var newParameter = ((ProcessInstanceDbModel) newQueueItem.parameter());
    assertThat(newParameter.state()).isEqualTo(ProcessInstanceState.COMPLETED);
    assertThat(newParameter.endDate()).isEqualTo(NOW);
  }

  @ParameterizedTest
  @MethodSource
  void statusUpdateToInsertMergerCanMerge(final QueueItem item, final boolean result) {
    final var merger =
        new UpsertMerger<>(ContextType.PROCESS_INSTANCE, 1L, ProcessInstanceDbModel.class, b -> b);

    assertThat(merger.canBeMerged(item)).isEqualTo(result);
  }

  static Stream<Arguments> statusUpdateToInsertMergerCanMerge() {
    return Stream.of(
        Arguments.of(
            new QueueItem(
                ContextType.PROCESS_INSTANCE, 1L, "statement1", mock(ProcessInstanceDbModel.class)),
            true),
        Arguments.of(new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", "bla"), false),
        Arguments.of(new QueueItem(ContextType.PROCESS_INSTANCE, 1L, "statement1", null), false),
        Arguments.of(new QueueItem(ContextType.PROCESS_INSTANCE, 2L, "statement1", null), false),
        Arguments.of(new QueueItem(ContextType.FLOW_NODE, 1L, "statement1", null), false));
  }

  public static ProcessInstanceDbModel createRandomized(
      final Function<ProcessInstanceDbModelBuilder, ProcessInstanceDbModelBuilder>
          builderFunction) {
    final var random = new Random();
    final var builder =
        new ProcessInstanceDbModelBuilder()
            .processInstanceKey(random.nextLong())
            .processInstanceKey(random.nextLong())
            .processDefinitionKey(random.nextLong())
            .processDefinitionId("process-" + random.nextInt(10000))
            .startDate(NOW.plus(random.nextInt(), ChronoUnit.MILLIS))
            .endDate(NOW.plus(random.nextInt(), ChronoUnit.MILLIS))
            .state(ProcessInstanceState.COMPLETED)
            .parentProcessInstanceKey(random.nextLong())
            .parentElementInstanceKey(random.nextLong())
            .tenantId("tenant-" + random.nextInt(10000))
            .version(random.nextInt());

    return builderFunction.apply(builder).build();
  }
}

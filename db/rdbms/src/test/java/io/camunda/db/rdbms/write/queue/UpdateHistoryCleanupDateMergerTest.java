/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.sql.ProcessInstanceMapper.UpdateHistoryCleanupDateDto;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UpdateHistoryCleanupDateMergerTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final OffsetDateTime LATER = OffsetDateTime.now().plus(500, ChronoUnit.MILLIS);
  private static final OffsetDateTime MUCH_LATER = OffsetDateTime.now().plusDays(1);

  @Test
  void shouldAddNewProcessInstanceKey() {
    final var merger = new UpdateHistoryCleanupDateMerger(ContextType.PROCESS_INSTANCE, 2L, NOW);

    final var parameter = new UpdateHistoryCleanupDateDto(List.of(1L), NOW);
    final var queueItem =
        new QueueItem(
            ContextType.PROCESS_INSTANCE, WriteStatementType.UPDATE, 1L, "statement", parameter);
    final var newQueueItem = merger.merge(queueItem);

    assertThat(newQueueItem.parameter())
        .asInstanceOf(InstanceOfAssertFactories.type(UpdateHistoryCleanupDateDto.class))
        .satisfies(
            p -> {
              assertThat(p.processInstanceKeys()).containsExactly(1L, 2L);
              assertThat(p.historyCleanupDate()).isEqualTo(NOW);
            });
  }

  @ParameterizedTest
  @MethodSource
  void statusUpdateToInsertMergerCanMerge(final QueueItem item, final boolean result) {
    final var merger = new UpdateHistoryCleanupDateMerger(ContextType.PROCESS_INSTANCE, 2L, NOW);

    assertThat(merger.canBeMerged(item)).isEqualTo(result);
  }

  static Stream<Arguments> statusUpdateToInsertMergerCanMerge() {
    return Stream.of(
        Arguments.of(
            new QueueItem(
                ContextType.PROCESS_INSTANCE,
                WriteStatementType.UPDATE,
                1L,
                "statement1",
                new UpdateHistoryCleanupDateDto(List.of(1L), NOW)),
            true),
        Arguments.of(
            new QueueItem(
                ContextType.PROCESS_INSTANCE,
                WriteStatementType.UPDATE,
                1L,
                "statement1",
                new UpdateHistoryCleanupDateDto(List.of(1L), LATER)),
            true),
        Arguments.of(
            new QueueItem(
                ContextType.PROCESS_INSTANCE,
                WriteStatementType.UPDATE,
                1L,
                "statement1",
                new UpdateHistoryCleanupDateDto(List.of(1L), MUCH_LATER)),
            false),
        Arguments.of(
            new QueueItem(
                ContextType.PROCESS_INSTANCE,
                WriteStatementType.UPDATE,
                1L,
                "statement1",
                new UpdateHistoryCleanupDateDto(LongStream.range(0, 1000).boxed().toList(), NOW)),
            false),
        Arguments.of(
            new QueueItem(
                ContextType.PROCESS_INSTANCE, WriteStatementType.UPDATE, 1L, "statement1", "bla"),
            false),
        Arguments.of(
            new QueueItem(
                ContextType.DECISION_INSTANCE,
                WriteStatementType.UPDATE,
                1L,
                "statement1",
                new UpdateHistoryCleanupDateDto(LongStream.range(0, 1000).boxed().toList(), NOW)),
            false));
  }
}

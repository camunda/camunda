/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.migrationvariablebackfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskVariableEntity;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

final class MigrationVariableBackfillTaskTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final int BATCH_SIZE = 10;
  private static final int VARIABLE_SIZE_THRESHOLD = 8191;

  private final MigrationVariableBackfillRepository repository =
      Mockito.mock(MigrationVariableBackfillRepository.class);

  private final MigrationVariableBackfillTask underTest =
      new MigrationVariableBackfillTask(
          repository,
          BATCH_SIZE,
          VARIABLE_SIZE_THRESHOLD,
          LoggerFactory.getLogger(MigrationVariableBackfillTaskTest.class));

  @BeforeEach
  void setUp() {
    when(repository.getPendingBackfillBatch(anyInt()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));
    when(repository.bulkUpsertTaskVariables(Mockito.anyList()))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(repository.deletePendingBackfillEntries(Mockito.anyList()))
        .thenReturn(CompletableFuture.completedFuture(null));
  }

  @Test
  void shouldReturnZeroWhenBatchIsEmpty() {
    // when
    final var result = underTest.execute();

    // then
    assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(0);
  }

  @Test
  void shouldQueryUsingConfiguredBatchSize() {
    // when
    underTest.execute().toCompletableFuture().join();

    // then
    verify(repository).getPendingBackfillBatch(BATCH_SIZE);
  }

  @Test
  void shouldNotFetchVariablesWhenBatchIsEmpty() {
    // when
    underTest.execute().toCompletableFuture().join();

    // then
    verify(repository, never()).getVariablesByProcessInstanceKey(Mockito.anyLong());
  }

  @Test
  void shouldNotUpsertWhenBatchIsEmpty() {
    // when
    underTest.execute().toCompletableFuture().join();

    // then
    verify(repository, never()).bulkUpsertTaskVariables(Mockito.anyList());
  }

  @Test
  void shouldNotDeleteWhenBatchIsEmpty() {
    // when
    underTest.execute().toCompletableFuture().join();

    // then
    verify(repository, never()).deletePendingBackfillEntries(Mockito.anyList());
  }

  @Nested
  final class WithPendingBatch {

    private final long processInstanceKey = 100L;

    @BeforeEach
    void setUp() {
      when(repository.getPendingBackfillBatch(anyInt()))
          .thenReturn(CompletableFuture.completedFuture(List.of(processInstanceKey)));
      when(repository.deletePendingBackfillEntries(Mockito.anyList()))
          .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldReturnVariableCountWhenVariablesExist() {
      // given
      final var variables = List.of(buildVariable(processInstanceKey, 1L, "foo", "bar"));
      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(CompletableFuture.completedFuture(variables));

      // when
      final var result = underTest.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroWhenNoVariablesFoundForInstance() {
      // given
      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      // when
      final var result = underTest.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(0);
    }

    @Test
    void shouldDeleteProcessedEntriesAfterBackfill() {
      // given
      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(buildVariable(processInstanceKey, 1L, "x", "1"))));

      // when
      underTest.execute().toCompletableFuture().join();

      // then
      verify(repository, times(1)).deletePendingBackfillEntries(List.of(processInstanceKey));
    }

    @Test
    void shouldDeleteEvenWhenInstanceHasNoVariables() {
      // given
      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      // when
      underTest.execute().toCompletableFuture().join();

      // then
      verify(repository, times(1)).deletePendingBackfillEntries(List.of(processInstanceKey));
    }

    @Test
    void shouldUpsertTaskVariableWithCorrectFields() {
      // given
      final long scopeKey = 200L;
      final var variable = buildVariable(processInstanceKey, scopeKey, "price", "42");
      variable.setPartitionId(1);
      variable.setTenantId("tenant-a");

      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(CompletableFuture.completedFuture(List.of(variable)));

      @SuppressWarnings("unchecked")
      final ArgumentCaptor<List<TaskVariableEntity>> captor = ArgumentCaptor.forClass(List.class);

      // when
      underTest.execute().toCompletableFuture().join();

      // then
      verify(repository, times(1)).bulkUpsertTaskVariables(captor.capture());
      final var taskVar = captor.getValue().getFirst();
      assertThat(taskVar.getId()).isEqualTo(scopeKey + "-price");
      assertThat(taskVar.getName()).isEqualTo("price");
      assertThat(taskVar.getValue()).isEqualTo("42");
      assertThat(taskVar.getScopeKey()).isEqualTo(scopeKey);
      assertThat(taskVar.getProcessInstanceId()).isEqualTo(processInstanceKey);
      assertThat(taskVar.getPartitionId()).isEqualTo(1);
      assertThat(taskVar.getTenantId()).isEqualTo("tenant-a");
      assertThat(taskVar.getJoin().getName())
          .isEqualTo(TaskJoinRelationshipType.PROCESS_VARIABLE.getType());
      assertThat(taskVar.getJoin().getParent()).isEqualTo(processInstanceKey);
    }

    @Test
    void shouldMapIsPreviewToIsTruncated() {
      // given
      final var truncatedVariable = buildVariable(processInstanceKey, 200L, "x", "truncated");
      truncatedVariable.setIsPreview(true);

      final var fullVariable = buildVariable(processInstanceKey, 201L, "y", "full");
      fullVariable.setIsPreview(false);

      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(CompletableFuture.completedFuture(List.of(truncatedVariable, fullVariable)));

      @SuppressWarnings("unchecked")
      final ArgumentCaptor<List<TaskVariableEntity>> captor = ArgumentCaptor.forClass(List.class);

      // when
      underTest.execute().toCompletableFuture().join();

      // then
      verify(repository).bulkUpsertTaskVariables(captor.capture());
      final var vars = captor.getValue();
      assertThat(vars).hasSize(2);
      assertThat(vars.stream().filter(v -> v.getName().equals("x")).findFirst())
          .hasValueSatisfying(v -> assertThat(v.getIsTruncated()).isTrue());
      assertThat(vars.stream().filter(v -> v.getName().equals("y")).findFirst())
          .hasValueSatisfying(v -> assertThat(v.getIsTruncated()).isFalse());
    }

    @Test
    void shouldUpsertVariablesForEachProcessInstanceInBatch() {
      // given
      final long secondInstanceKey = 200L;
      when(repository.getPendingBackfillBatch(anyInt()))
          .thenReturn(
              CompletableFuture.completedFuture(List.of(processInstanceKey, secondInstanceKey)));

      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(buildVariable(processInstanceKey, 10L, "a", "1"))));
      when(repository.getVariablesByProcessInstanceKey(secondInstanceKey))
          .thenReturn(
              CompletableFuture.completedFuture(
                  List.of(
                      buildVariable(secondInstanceKey, 20L, "b", "2"),
                      buildVariable(secondInstanceKey, 21L, "c", "3"))));

      // when
      final var result = underTest.execute();

      // then
      assertThat(result).succeedsWithin(TIMEOUT).isEqualTo(3);
      verify(repository, times(1)).bulkUpsertTaskVariables(Mockito.argThat(l -> l.size() == 1));
      verify(repository, times(1)).bulkUpsertTaskVariables(Mockito.argThat(l -> l.size() == 2));
    }

    @Test
    void shouldNotUpsertWhenInstanceHasNoVariables() {
      // given
      when(repository.getVariablesByProcessInstanceKey(processInstanceKey))
          .thenReturn(CompletableFuture.completedFuture(List.of()));

      // when
      underTest.execute().toCompletableFuture().join();

      // then
      verify(repository, never()).bulkUpsertTaskVariables(Mockito.anyList());
    }
  }

  // --- helpers ---

  private static VariableEntity buildVariable(
      final long processInstanceKey, final long scopeKey, final String name, final String value) {
    final var v = new VariableEntity();
    v.setProcessInstanceKey(processInstanceKey);
    v.setScopeKey(scopeKey);
    v.setName(name);
    v.setValue(value);
    v.setIsPreview(false);
    return v;
  }
}

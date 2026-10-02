/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ItemProviderFactoryTest {

  private final SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);
  private final ItemProviderFactory factory =
      new ItemProviderFactory(searchClientsProxy, metrics, 1);

  @Test
  void shouldSetFiltersForCancelProcessInstance() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations())
        .containsExactly(Operation.exists(false));
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.in("ACTIVE", "SUSPENDED"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldNarrowStateAndOverrideParentFiltersForCancelProcessInstance() {
    // given
    final var filter =
        new ProcessInstanceFilter.Builder()
            .states("SUSPENDED")
            .parentProcessInstanceKeys(12345L)
            .processInstanceKeys(67890L)
            .build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations())
        .containsExactly(Operation.exists(false));
    assertThat(usedFilter.stateOperations())
        .containsExactly(Operation.eq("SUSPENDED"), Operation.in("ACTIVE", "SUSPENDED"));
    assertThat(usedFilter.processInstanceKeyOperations()).containsExactly(Operation.eq(67890L));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  static Stream<Operation<String>> callerStateOperations() {
    return Stream.of(
        Operation.eq("ACTIVE"),
        Operation.eq("SUSPENDED"),
        Operation.in("ACTIVE", "SUSPENDED"),
        Operation.neq("ACTIVE"),
        Operation.neq("COMPLETED"),
        Operation.exists(true),
        Operation.like("SUSP*"));
  }

  @ParameterizedTest
  @MethodSource("callerStateOperations")
  void shouldKeepCallerStateFilterAndNarrowItForCancelProcessInstance(
      final Operation<String> callerStateOperation) {
    // given
    final var filter =
        new ProcessInstanceFilter.Builder().stateOperations(callerStateOperation).build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.stateOperations())
        .containsExactly(callerStateOperation, Operation.in("ACTIVE", "SUSPENDED"));
  }

  @Test
  void shouldKeepStateFiltersInOrBranchesForCancelProcessInstance() {
    // given
    final var suspendedBranch = new ProcessInstanceFilter.Builder().states("SUSPENDED").build();
    final var incidentBranch = new ProcessInstanceFilter.Builder().hasIncident(true).build();
    final var filter =
        new ProcessInstanceFilter.Builder()
            .addOrOperation(suspendedBranch)
            .addOrOperation(incidentBranch)
            .build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.CANCEL_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.orFilters()).containsExactly(suspendedBranch, incidentBranch);
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.in("ACTIVE", "SUSPENDED"));
  }

  @Test
  void shouldSetFiltersForSuspendProcessInstance() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.SUSPEND_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForResumeProcessInstance() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.RESUME_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.eq("SUSPENDED"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForMigrateProcessInstance() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.MIGRATE_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForModifyProcessInstance() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.MODIFY_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  static Stream<NarrowCase> narrowCases() {
    return Stream.of(
        new NarrowCase(
            BatchOperationType.MIGRATE_PROCESS_INSTANCE,
            ProcessInstanceItemProvider.class,
            ip -> ((ProcessInstanceItemProvider) ip).getFilter()),
        new NarrowCase(
            BatchOperationType.MODIFY_PROCESS_INSTANCE,
            ProcessInstanceItemProvider.class,
            ip -> ((ProcessInstanceItemProvider) ip).getFilter()),
        new NarrowCase(
            BatchOperationType.RESOLVE_INCIDENT,
            IncidentItemProvider.class,
            ip -> ((IncidentItemProvider) ip).getFilter()));
  }

  static Stream<Arguments> narrowedCallerStateOperations() {
    return narrowCases()
        .flatMap(testCase -> callerStateOperations().map(op -> Arguments.of(testCase, op)));
  }

  /**
   * Unlike cancel (which replaces the caller's state filter), migrate/modify/resolve-incident
   * narrow it: the caller's state is kept and ANDed with ACTIVE, so a conflicting value narrows the
   * query to zero items instead of being silently discarded.
   */
  @ParameterizedTest(name = "{0} keeps caller state {1}")
  @MethodSource("narrowedCallerStateOperations")
  void shouldKeepCallerStateFilterAndNarrowIt(
      final NarrowCase testCase, final Operation<String> callerStateOperation) {
    // given
    final var filter =
        new ProcessInstanceFilter.Builder()
            .stateOperations(callerStateOperation)
            .parentProcessInstanceKeys(12345L)
            .build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType()).thenReturn(testCase.batchOperationType());
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isInstanceOf(testCase.expectedProviderType());
    final var usedFilter = testCase.filterExtractor().apply(itemProvider);
    assertThat(usedFilter.stateOperations())
        .containsExactly(callerStateOperation, Operation.eq("ACTIVE"));
    // unlike cancel/suspend/resume, migrate/modify/resolve-incident do not override
    // parentProcessInstanceKey
    assertThat(usedFilter.parentProcessInstanceKeyOperations())
        .containsExactly(Operation.eq(12345L));
  }

  /**
   * A state nested in an orFilters entry is ANDed with the top-level state by the underlying query,
   * so narrowing (unlike the replace approach cancel used to take) needs no special handling for
   * it: the branch is left untouched and the top-level ACTIVE narrows the whole query correctly,
   * matching Operate's own filter shape (e.g. an "Active + Incidents" toolbar filter sends a state
   * inside one $or branch).
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("narrowCases")
  void shouldKeepStateFiltersInOrBranches(final NarrowCase testCase) {
    // given
    final var completedBranch = new ProcessInstanceFilter.Builder().states("COMPLETED").build();
    final var incidentBranch = new ProcessInstanceFilter.Builder().hasIncident(true).build();
    final var filter =
        new ProcessInstanceFilter.Builder()
            .addOrOperation(completedBranch)
            .addOrOperation(incidentBranch)
            .build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType()).thenReturn(testCase.batchOperationType());
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    final var usedFilter = testCase.filterExtractor().apply(itemProvider);
    assertThat(usedFilter.orFilters()).containsExactly(completedBranch, incidentBranch);
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
  }

  @Test
  void shouldSetFiltersForResolveIncident() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType()).thenReturn(BatchOperationType.RESOLVE_INCIDENT);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(IncidentItemProvider.class);

    final var usedFilter = ((IncidentItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(usedFilter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("overrideCases")
  void shouldOverrideFilters(final OverrideCase testCase) {
    // given
    final var filter =
        new ProcessInstanceFilter.Builder()
            .states("COMPLETED")
            .parentProcessInstanceKeys(12345L)
            .processInstanceKeys(67890L)
            .build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType()).thenReturn(testCase.batchOperationType());
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isInstanceOf(testCase.expectedProviderType());

    final var usedFilter = testCase.filterExtractor().apply(itemProvider);
    assertThat(usedFilter.stateOperations()).isEqualTo(testCase.expectedStateOperations());
    assertThat(usedFilter.parentProcessInstanceKeyOperations())
        .isEqualTo(testCase.expectedParentProcessInstanceKeyOperations());
    assertThat(usedFilter.processInstanceKeyOperations()).containsExactly(Operation.eq(67890L));
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  private static Stream<OverrideCase> overrideCases() {
    // CANCEL_PROCESS_INSTANCE is intentionally excluded: since #64350 it narrows the caller's
    // state filter with ACTIVE/SUSPENDED instead of replacing it, which is covered separately by
    // shouldNarrowStateAndOverrideParentFiltersForCancelProcessInstance and
    // shouldKeepCallerStateFilterAndNarrowItForCancelProcessInstance above.
    //
    // MIGRATE_PROCESS_INSTANCE, MODIFY_PROCESS_INSTANCE and RESOLVE_INCIDENT are also excluded:
    // they narrow rather than replace the state filter, same as cancel, and are covered by
    // shouldKeepCallerStateFilterAndNarrowIt and shouldKeepStateFiltersInOrBranches above.
    return Stream.of(
        new OverrideCase(
            BatchOperationType.SUSPEND_PROCESS_INSTANCE,
            ProcessInstanceItemProvider.class,
            ip -> ((ProcessInstanceItemProvider) ip).getFilter(),
            List.of(Operation.eq("ACTIVE")),
            List.of()),
        new OverrideCase(
            BatchOperationType.RESUME_PROCESS_INSTANCE,
            ProcessInstanceItemProvider.class,
            ip -> ((ProcessInstanceItemProvider) ip).getFilter(),
            List.of(Operation.eq("SUSPENDED")),
            List.of()));
  }

  @Test
  void shouldSetFiltersForUpdateJob() {
    // given
    final var filter = new JobFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType()).thenReturn(BatchOperationType.UPDATE_JOB);
    when(batchOperation.getEntityFilter(JobFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(JobItemProvider.class);

    final var usedFilter = ((JobItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.partitionId()).isEqualTo(1);
    // the non-terminal job states are enriched onto the filter as a single 'in' operation
    assertThat(usedFilter.stateOperations())
        .containsExactly(
            Operation.in(
                JobState.CREATED.name(),
                JobState.FAILED.name(),
                JobState.ERROR_THROWN.name(),
                JobState.TIMED_OUT.name(),
                JobState.RETRIES_UPDATED.name(),
                JobState.PRIORITY_UPDATED.name(),
                JobState.TIMEOUT_UPDATED.name(),
                JobState.MIGRATED.name()));
  }

  @Test
  void shouldSetFiltersForDeleteProcessInstance() {
    // given
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.DELETE_PROCESS_INSTANCE);
    when(batchOperation.getEntityFilter(ProcessInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(ProcessInstanceItemProvider.class);

    final var usedFilter = ((ProcessInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  @Test
  void shouldSetFiltersForDeleteDecisionInstance() {
    // given
    final var filter = new DecisionInstanceFilter.Builder().build();
    final var batchOperation = mock(PersistedBatchOperation.class);
    when(batchOperation.getBatchOperationType())
        .thenReturn(BatchOperationType.DELETE_DECISION_INSTANCE);
    when(batchOperation.getEntityFilter(DecisionInstanceFilter.class)).thenReturn(filter);

    // when
    final var itemProvider = factory.fromBatchOperation(batchOperation);

    // then
    assertThat(itemProvider).isNotNull();
    assertThat(itemProvider).isInstanceOf(DecisionInstanceItemProvider.class);

    final var usedFilter = ((DecisionInstanceItemProvider) itemProvider).getFilter();
    assertThat(usedFilter.partitionId()).isEqualTo(1);
  }

  private record OverrideCase(
      BatchOperationType batchOperationType,
      Class<? extends ItemProvider> expectedProviderType,
      Function<ItemProvider, ProcessInstanceFilter> filterExtractor,
      List<Operation<String>> expectedStateOperations,
      List<Operation<Long>> expectedParentProcessInstanceKeyOperations) {

    @Override
    public String toString() {
      return batchOperationType.name();
    }
  }

  private record NarrowCase(
      BatchOperationType batchOperationType,
      Class<? extends ItemProvider> expectedProviderType,
      Function<ItemProvider, ProcessInstanceFilter> filterExtractor) {

    @Override
    public String toString() {
      return batchOperationType.name();
    }
  }
}

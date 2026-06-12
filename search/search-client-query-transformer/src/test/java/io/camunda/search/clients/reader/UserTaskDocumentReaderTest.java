/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserTaskDocumentReaderTest {

  private static final int VARIABLE_PAGE_SIZE = 10_000;

  private SearchClientBasedQueryExecutor executor;
  private UserTaskDocumentReader reader;
  private ResourceAccessChecks checks;

  @BeforeEach
  void setUp() {
    executor = mock(SearchClientBasedQueryExecutor.class);
    reader = new UserTaskDocumentReader(executor, mock(IndexDescriptor.class));
    checks = ResourceAccessChecks.disabled();
  }

  private static VariableValueFilter variableValueFilter(
      final String name, final UntypedOperation... operations) {
    return new VariableValueFilter.Builder()
        .name(name)
        .valueOperations(List.of(operations))
        .build();
  }

  private static VariableEntity variable(final long variableKey, final long processInstanceKey) {
    return new VariableEntity(
        variableKey,
        "amount",
        "1000",
        null,
        false,
        processInstanceKey,
        processInstanceKey,
        null,
        "process",
        "<default>");
  }

  private static SearchQueryResult<VariableEntity> variablesPage(
      final String endCursor, final Long... processInstanceKeys) {
    final var items =
        LongStream.range(0, processInstanceKeys.length)
            .mapToObj(i -> variable(i, processInstanceKeys[(int) i]))
            .toList();
    return new SearchQueryResult<>(items.size(), false, items, null, endCursor);
  }

  @SafeVarargs
  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubVariableSearch(
      final SearchQueryResult<VariableEntity> first,
      final SearchQueryResult<VariableEntity>... more) {
    when(executor.search(
            any(VariableQuery.class),
            eq(io.camunda.webapps.schema.entities.VariableEntity.class),
            any(ResourceAccessChecks.class)))
        .thenReturn((SearchQueryResult) first, (SearchQueryResult[]) more);
  }

  @Test
  void shouldNotQueryVariablesWhenNoProcessInstanceVariableFilter() {
    // given
    final var query = UserTaskQuery.of(q -> q);
    when(executor.search(eq(query), eq(TaskEntity.class), eq(checks)))
        .thenReturn(SearchQueryResult.empty());

    // when
    reader.search(query, checks);

    // then
    verify(executor).search(eq(query), eq(TaskEntity.class), eq(checks));
    verify(executor, never()).search(any(VariableQuery.class), any(), any());
  }

  @Test
  void shouldRewriteProcessInstanceVariableFilterToProcessInstanceKeys() {
    // given
    final var pair = variableValueFilter("amount", UntypedOperation.of(Operation.eq("1000")));
    final var query =
        UserTaskQuery.of(
            q -> q.filter(f -> f.processInstanceVariables(List.of(pair))).page(p -> p.size(50)));
    stubVariableSearch(variablesPage(null, 10L));
    when(executor.search(any(UserTaskQuery.class), eq(TaskEntity.class), eq(checks)))
        .thenReturn(SearchQueryResult.empty());

    // when
    reader.search(query, checks);

    // then
    final var queryCaptor = ArgumentCaptor.forClass(UserTaskQuery.class);
    verify(executor).search(queryCaptor.capture(), eq(TaskEntity.class), eq(checks));
    final var rewritten = queryCaptor.getValue();
    assertThat(rewritten.filter().processInstanceVariableFilter()).isEmpty();
    assertThat(rewritten.filter().processInstanceKeyOperations())
        .containsExactly(Operation.in(List.of(10L)));
    assertThat(rewritten.sort()).isEqualTo(query.sort());
    assertThat(rewritten.page()).isEqualTo(query.page());
  }

  @Test
  void shouldForwardCallerFiltersToVariableSearch() {
    // given
    final var pair = variableValueFilter("amount", UntypedOperation.of(Operation.eq("1000")));
    final var query =
        UserTaskQuery.of(
            q ->
                q.filter(
                    f ->
                        f.processInstanceKeys(42L)
                            .processDefinitionKeys(7L)
                            .processInstanceVariables(List.of(pair))));
    stubVariableSearch(variablesPage(null, 42L));
    when(executor.search(any(UserTaskQuery.class), eq(TaskEntity.class), eq(checks)))
        .thenReturn(SearchQueryResult.empty());

    // when
    reader.search(query, checks);

    // then - the variable search is narrowed by the caller's key filters
    final var variableQueryCaptor = ArgumentCaptor.forClass(VariableQuery.class);
    verify(executor)
        .search(
            variableQueryCaptor.capture(),
            eq(io.camunda.webapps.schema.entities.VariableEntity.class),
            any(ResourceAccessChecks.class));
    final var variableFilter = variableQueryCaptor.getValue().filter();
    assertThat(variableFilter.nameOperations()).containsExactly(Operation.eq("amount"));
    assertThat(variableFilter.valueOperations())
        .containsExactly(UntypedOperation.of(Operation.eq("1000")));
    assertThat(variableFilter.processInstanceKeyOperations()).containsExactly(Operation.eq(42L));
    assertThat(variableFilter.processDefinitionKeyOperations()).containsExactly(Operation.eq(7L));

    // and - the rewritten user task filter keeps the caller's operations and adds the keys
    final var queryCaptor = ArgumentCaptor.forClass(UserTaskQuery.class);
    verify(executor).search(queryCaptor.capture(), eq(TaskEntity.class), eq(checks));
    assertThat(queryCaptor.getValue().filter().processInstanceKeyOperations())
        .containsExactly(Operation.eq(42L), Operation.in(List.of(42L)));
    assertThat(queryCaptor.getValue().filter().processDefinitionKeyOperations())
        .containsExactly(Operation.eq(7L));
  }

  @Test
  void shouldReturnEmptyResultWhenNoVariableMatches() {
    // given
    final var pair = variableValueFilter("amount", UntypedOperation.of(Operation.eq("1000")));
    final var query =
        UserTaskQuery.of(q -> q.filter(f -> f.processInstanceVariables(List.of(pair))));
    stubVariableSearch(variablesPage(null));

    // when
    final var result = reader.search(query, checks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
    verify(executor, never()).search(any(UserTaskQuery.class), any(), any());
  }

  @Test
  void shouldIntersectProcessInstanceKeysAcrossVariableFilters() {
    // given
    final var first = variableValueFilter("amount", UntypedOperation.of(Operation.eq("1000")));
    final var second =
        variableValueFilter("customer", UntypedOperation.of(Operation.eq("\"acme\"")));
    final var query =
        UserTaskQuery.of(q -> q.filter(f -> f.processInstanceVariables(List.of(first, second))));
    stubVariableSearch(variablesPage(null, 1L, 2L), variablesPage(null, 2L, 3L));
    when(executor.search(any(UserTaskQuery.class), eq(TaskEntity.class), eq(checks)))
        .thenReturn(SearchQueryResult.empty());

    // when
    reader.search(query, checks);

    // then
    final var queryCaptor = ArgumentCaptor.forClass(UserTaskQuery.class);
    verify(executor).search(queryCaptor.capture(), eq(TaskEntity.class), eq(checks));
    assertThat(queryCaptor.getValue().filter().processInstanceKeyOperations())
        .containsExactly(Operation.in(List.of(2L)));
  }

  @Test
  void shouldReturnEmptyWhenIntersectionAcrossVariableFiltersIsEmpty() {
    // given
    final var first = variableValueFilter("amount", UntypedOperation.of(Operation.eq("1000")));
    final var second =
        variableValueFilter("customer", UntypedOperation.of(Operation.eq("\"acme\"")));
    final var query =
        UserTaskQuery.of(q -> q.filter(f -> f.processInstanceVariables(List.of(first, second))));
    stubVariableSearch(variablesPage(null, 1L), variablesPage(null, 2L));

    // when
    final var result = reader.search(query, checks);

    // then
    assertThat(result.items()).isEmpty();
    verify(executor, never()).search(any(UserTaskQuery.class), any(), any());
  }

  @Test
  void shouldPageThroughAllVariablePages() {
    // given - a first full page and a partial second page of matching variables
    final var fullPageKeys = LongStream.range(0, VARIABLE_PAGE_SIZE).boxed().toArray(Long[]::new);
    final var pair = variableValueFilter("amount", UntypedOperation.of(Operation.eq("1000")));
    final var query =
        UserTaskQuery.of(q -> q.filter(f -> f.processInstanceVariables(List.of(pair))));
    stubVariableSearch(
        variablesPage("cursor-1", fullPageKeys),
        variablesPage("cursor-2", (long) VARIABLE_PAGE_SIZE));
    when(executor.search(any(UserTaskQuery.class), eq(TaskEntity.class), eq(checks)))
        .thenReturn(SearchQueryResult.empty());

    // when
    reader.search(query, checks);

    // then - the second page is requested with the first page's end cursor
    final var variableQueryCaptor = ArgumentCaptor.forClass(VariableQuery.class);
    verify(executor, times(2))
        .search(
            variableQueryCaptor.capture(),
            eq(io.camunda.webapps.schema.entities.VariableEntity.class),
            any(ResourceAccessChecks.class));
    assertThat(variableQueryCaptor.getAllValues().get(0).page().after()).isNull();
    assertThat(variableQueryCaptor.getAllValues().get(1).page().after()).isEqualTo("cursor-1");

    // and - all keys from both pages end up in the rewritten filter
    final var queryCaptor = ArgumentCaptor.forClass(UserTaskQuery.class);
    verify(executor).search(queryCaptor.capture(), eq(TaskEntity.class), eq(checks));
    final var keyOperations = queryCaptor.getValue().filter().processInstanceKeyOperations();
    assertThat(keyOperations).hasSize(1);
    assertThat(keyOperations.getFirst().values()).hasSize(VARIABLE_PAGE_SIZE + 1);
  }

  @Test
  void shouldAndMultipleValueOperationsWithinSingleVariableFilter() {
    // given - a single variable filter with two value operations (e.g. a range)
    final var pair =
        variableValueFilter(
            "amount",
            UntypedOperation.of(Operation.gt("500")),
            UntypedOperation.of(Operation.lt("2000")));
    final var query =
        UserTaskQuery.of(q -> q.filter(f -> f.processInstanceVariables(List.of(pair))));
    stubVariableSearch(variablesPage(null, 10L));
    when(executor.search(any(UserTaskQuery.class), eq(TaskEntity.class), eq(checks)))
        .thenReturn(SearchQueryResult.empty());

    // when
    reader.search(query, checks);

    // then - a single variable search carries both operations (ANDed by the variable filter)
    final var variableQueryCaptor = ArgumentCaptor.forClass(VariableQuery.class);
    verify(executor)
        .search(
            variableQueryCaptor.capture(),
            eq(io.camunda.webapps.schema.entities.VariableEntity.class),
            any(ResourceAccessChecks.class));
    assertThat(variableQueryCaptor.getValue().filter().valueOperations())
        .containsExactly(
            UntypedOperation.of(Operation.gt("500")), UntypedOperation.of(Operation.lt("2000")));
  }
}

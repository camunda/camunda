/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemActorType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemOperationType;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import org.junit.jupiter.api.Test;

class VariableHistoryItemQueryTest {

  @Test
  void shouldBuildQueryForVariableKeySortedByItemKeyAscending() {
    // given
    final long variableKey = 2251799813685310L;

    // when
    final var query =
        SearchQueryBuilders.variableHistoryItemSearchQuery(
            b ->
                b.filter(f -> f.variableKeys(variableKey))
                    .sort(s -> s.variableHistoryItemKey().asc()));

    // then
    assertThat(query.filter().variableKeyOperations()).containsExactly(Operation.eq(variableKey));
    assertThat(query.sort().getFieldSortings())
        .containsExactly(new FieldSorting("variableHistoryItemKey", SortOrder.ASC));
  }

  @Test
  void shouldMapEnumFiltersToNames() {
    // when
    final var filter =
        FilterBuilders.variableHistoryItem(
            f ->
                f.variableOperationTypes(VariableOperationType.API)
                    .operationTypes(VariableHistoryItemOperationType.CREATE)
                    .actorTypes(VariableHistoryItemActorType.ENGINE));

    // then
    assertThat(filter.variableOperationTypeOperations()).containsExactly(Operation.eq("API"));
    assertThat(filter.operationTypeOperations()).containsExactly(Operation.eq("CREATE"));
    assertThat(filter.actorTypeOperations()).containsExactly(Operation.eq("ENGINE"));
  }

  @Test
  void shouldDefaultToEmptyFilterAndSort() {
    // when
    final var query = SearchQueryBuilders.variableHistoryItemSearchQuery(b -> b);

    // then
    assertThat(query.filter().variableKeyOperations()).isEmpty();
    assertThat(query.sort().getFieldSortings()).isEmpty();
  }
}

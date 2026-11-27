/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.CaseStageFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseQueryTest {

  @Test
  void shouldDefaultToEmptyFilterAndSort() {
    // when
    final var query = SearchQueryBuilders.caseSearchQuery().build();

    // then
    final var filter = query.filter();
    assertThat(filter.caseKeyOperations()).isNotNull().isEmpty();
    assertThat(filter.processInstanceKeyOperations()).isNotNull().isEmpty();
    assertThat(filter.processDefinitionIdOperations()).isNotNull().isEmpty();
    assertThat(filter.businessIdOperations()).isNotNull().isEmpty();
    assertThat(filter.titleOperations()).isNotNull().isEmpty();
    assertThat(filter.stateOperations()).isNotNull().isEmpty();
    assertThat(filter.tenantIdOperations()).isNotNull().isEmpty();
    assertThat(filter.stageFilters()).isNotNull().isEmpty();
    assertThat(query.sort().getFieldSortings()).isNotNull().isEmpty();
  }

  @Test
  void shouldAccumulateKeyOperations() {
    // when
    final var query =
        SearchQueryBuilders.caseSearchQuery(
            q -> q.filter(f -> f.caseKeys(1L).caseKeys(2L).processInstanceKeys(3L)));

    // then
    assertThat(query.filter().caseKeyOperations())
        .containsExactly(Operation.eq(1L), Operation.eq(2L));
    assertThat(query.filter().processInstanceKeyOperations()).containsExactly(Operation.eq(3L));
  }

  @Test
  void shouldSetStringOperations() {
    // when
    final var query =
        SearchQueryBuilders.caseSearchQuery(
            q ->
                q.filter(
                    f ->
                        f.processDefinitionIds("p")
                            .businessIds("b")
                            .titleOperations(Operation.like("Loan*"))
                            .states("ACTIVE")
                            .tenantIds("t")));

    // then
    final var filter = query.filter();
    assertThat(filter.processDefinitionIdOperations()).containsExactly(Operation.eq("p"));
    assertThat(filter.businessIdOperations()).containsExactly(Operation.eq("b"));
    assertThat(filter.titleOperations()).containsExactly(Operation.like("Loan*"));
    assertThat(filter.stateOperations()).containsExactly(Operation.eq("ACTIVE"));
    assertThat(filter.tenantIdOperations()).containsExactly(Operation.eq("t"));
  }

  @Test
  void shouldAccumulateStageFilters() {
    // when
    final var query =
        SearchQueryBuilders.caseSearchQuery(
            q ->
                q.filter(
                    f ->
                        f.stages(s -> s.elementIds("Stage_Review").statuses("ACTIVE"))
                            .stages(s -> s.elementIds("Stage_Intake").statuses("COMPLETED"))));

    // then
    assertThat(query.filter().stageFilters())
        .containsExactly(
            new CaseStageFilter(
                List.of(Operation.eq("Stage_Review")), List.of(Operation.eq("ACTIVE"))),
            new CaseStageFilter(
                List.of(Operation.eq("Stage_Intake")), List.of(Operation.eq("COMPLETED"))));
  }

  @Test
  void shouldBuildSortOrderings() {
    // when
    final var query =
        SearchQueryBuilders.caseSearchQuery(q -> q.sort(s -> s.title().asc().state().desc()));

    // then
    assertThat(query.sort().getFieldSortings())
        .containsExactly(
            new FieldSorting("title", SortOrder.ASC), new FieldSorting("state", SortOrder.DESC));
  }
}

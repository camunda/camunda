/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.sort.SearchSortOptions;
import io.camunda.search.sort.SortOrder;
import io.camunda.search.sort.UserTaskSort;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class UserTaskSortTest extends AbstractSortTransformerTest {

  @Test
  public void shouldSortByPriority() {
    // given
    final var request =
        SearchQueryBuilders.userTaskSearchQuery(
            u -> u.sort(UserTaskSort.of(s -> s.priority().asc())));

    // when
    final var sort = transformRequest(request);

    // then
    assertThat(sort).hasSize(2);
    assertThat(sort.get(0))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo("priority");
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
    assertThat(sort.get(1))
        .isInstanceOfSatisfying(
            SearchSortOptions.class,
            t -> {
              assertThat(t.field().field()).isEqualTo("key");
              assertThat(t.field().order()).isEqualTo(SortOrder.ASC);
            });
  }

  @Test
  public void shouldApplySortConditionByCreationDate() {
    // given
    final var userTaskStateFilter = FilterBuilders.userTask((f) -> f.states("CREATED"));
    final var request =
        SearchQueryBuilders.userTaskSearchQuery(
            (q) -> q.filter(userTaskStateFilter).sort((s) -> s.creationDate().asc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2); // Assert has key + creationTime

    // Check if "creationTime" is present in any position
    final boolean creationTimeAscPresent =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("creationTime")
                        && s.field().order().equals(SortOrder.ASC));

    assertThat(creationTimeAscPresent).isTrue();
  }

  @Test
  public void shouldApplySortConditionByCompletionDate() {
    // given
    final var userTaskStateFilter = FilterBuilders.userTask((f) -> f.states("CREATED"));
    final var request =
        SearchQueryBuilders.userTaskSearchQuery(
            (q) -> q.filter(userTaskStateFilter).sort((s) -> s.completionDate().desc()));

    // when
    final var sort = transformRequest(request);

    // then
    Assertions.assertThat(sort).isNotNull();
    Assertions.assertThat(sort).hasSize(2); // Assert has key + creationTime

    // Check if " completionTime" is present in any position
    final boolean completionDateDesc =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("completionTime")
                        && s.field().order().equals(SortOrder.DESC));

    assertThat(completionDateDesc).isTrue();
  }
}

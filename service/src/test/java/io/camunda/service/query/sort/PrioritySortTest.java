/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.sort;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.service.UserTaskServices;
import io.camunda.service.query.filter.UserTaskSearchQueryStub;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.sort.SearchSortOptions;
import io.camunda.service.search.sort.SortOrder;
import io.camunda.service.search.sort.UserTaskSort;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PrioritySortTest {
  private UserTaskServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new UserTaskSearchQueryStub().registerWith(client);
    services = new UserTaskServices(null, null, null);
  }

  @Test
  public void shouldSortByPriority() {
    services.search(
        SearchQueryBuilders.userTaskSearchQuery(
            u -> u.sort(UserTaskSort.of(s -> s.priority().asc()))));

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();

    final var sort = searchRequest.sort();
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
}

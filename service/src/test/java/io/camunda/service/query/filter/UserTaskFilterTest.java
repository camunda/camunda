/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.UserTaskServices;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UserTaskFilterTest {

  private UserTaskServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new UserTaskSearchQueryStub().registerWith(client);
    services = new UserTaskServices(client);
  }

  @Test
  public void shouldQueryOnlyByUserTasks() {
    // given
    final UserTaskFilter filter = new UserTaskFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final SearchQueryRequest searchRequest = client.getSingleSearchRequest();
    final SearchQueryOption queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("implementation");
              assertThat(t.value().stringValue()).isEqualTo("ZEEBE_USER_TASK");
            });
  }

  @Test
  public void shouldReturnUserTasks() {
    // given
    final UserTaskFilter filter = new UserTaskFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(filter));

    // when
    final SearchQueryResult<UserTaskEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);

    final UserTaskEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(123L);
  }

  @Test
  public void shouldQueryByUserTaskKey() {
    // given
    final var userTaskFilter = FilterBuilders.userTask((f) -> f.userTaskKeys(4503599627370497L));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((q) -> q.filter(userTaskFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();

    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("key");
                        assertThat(term.value().longValue()).isEqualTo(4503599627370497L);
                      });
            });
  }

  @Test
  public void shouldQueryByTaskState() {
    // given
    final var taskStateFilter = FilterBuilders.userTask((f) -> f.taskStates("CREATED"));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(taskStateFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("state");
                        assertThat(term.value().stringValue()).isEqualTo("CREATED");
                      });
            });
  }

  @Test
  public void shouldQueryByAssignee() {
    // given
    final var assigneeFilter = FilterBuilders.userTask((f) -> f.assignees("assignee1"));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(assigneeFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("assignee");
                        assertThat(term.value().stringValue()).isEqualTo("assignee1");
                      });
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var tenantFilter = FilterBuilders.userTask((f) -> f.tenantIds("tenant1"));
    final var searchQuery = SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(tenantFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();

    searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            (t) -> {
              assertThat(t.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      (term) -> {
                        assertThat(term.field()).isEqualTo("tenantId");
                        assertThat(term.value().stringValue()).isEqualTo("tenant1");
                      });
            });
  }
}

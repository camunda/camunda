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
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.sort.SortOrder;
import io.camunda.service.UserTaskServices;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.filter.ComparableValueFilter;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.UserTaskFilter.Builder;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UserTaskFilterTest {

  private UserTaskServices services;
  private StubbedCamundaSearchClient client;
  private StubbedBrokerClient brokerClient;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new UserTaskSearchQueryStub().registerWith(client);
    services = new UserTaskServices(brokerClient, client);
  }

  @Test
  public void shouldQueryOnlyByUserTasks() {
    // given
    final UserTaskFilter filter = new Builder().build();
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
    final UserTaskFilter filter = new Builder().build();
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
  public void shouldApplySortConditionByCreationDate() {
    // given
    final var userTaskStateFilter = FilterBuilders.userTask((f) -> f.states(FilterOperator.EQ,
        Collections.singletonList("CREATED")));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery(
            (q) -> q.filter(userTaskStateFilter).sort((s) -> s.creationDate().asc()));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    // Assert the sort condition
    final var sort = searchRequest.sort();
    assertThat(sort).isNotNull();
    assertThat(sort).hasSize(2); // Assert has key + creationTime

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
    final var userTaskStateFilter = FilterBuilders.userTask((f) -> f.states(FilterOperator.EQ,
        Collections.singletonList("CREATED")));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery(
            (q) -> q.filter(userTaskStateFilter).sort((s) -> s.completionDate().desc()));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    // Assert the sort condition
    final var sort = searchRequest.sort();
    assertThat(sort).isNotNull();
    assertThat(sort).hasSize(2); // Assert has key + creationTime

    // Check if " completionTime" is present in any position
    final boolean completionDateDesc =
        sort.stream()
            .anyMatch(
                s ->
                    s.field().field().equals("completionTime")
                        && s.field().order().equals(SortOrder.DESC));

    assertThat(completionDateDesc).isTrue();
  }

  @Test
  public void shouldQueryByUserTaskKey() {
    // given
    final var userTaskFilter = FilterBuilders.userTask((f) -> f.keys(4503599627370497L));
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
    final var taskStateFilter = FilterBuilders.userTask((f) -> f.states(FilterOperator.EQ, Collections.singletonList("CREATED")));
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

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // given
    final var processInstanceKeyFilter =
        FilterBuilders.userTask((f) -> f.processInstanceKeys(12345L));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(processInstanceKeyFilter));

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
                        assertThat(term.field()).isEqualTo("processInstanceId");
                        assertThat(term.value().longValue()).isEqualTo(12345L);
                      });
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    // given
    final var processDefinitionKeyFilter =
        FilterBuilders.userTask((f) -> f.processDefinitionKeys(123L));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(processDefinitionKeyFilter));

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
                        assertThat(term.field()).isEqualTo("processDefinitionId");
                        assertThat(term.value().longValue()).isEqualTo(123L);
                      });
            });
  }

  @Test
  public void shouldQueryByBpmnProcessId() {
    // given
    final var bpmnProcessIdFilter =
        FilterBuilders.userTask((f) -> f.bpmnProcessIds("bpmnProcess1"));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(bpmnProcessIdFilter));

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
                        assertThat(term.field()).isEqualTo("bpmnProcessId");
                        assertThat(term.value().stringValue()).isEqualTo("bpmnProcess1");
                      });
            });
  }

  @Test
  public void shouldQueryByCandidateUsers() {
    // given
    final var candidateUsersFilter =
        FilterBuilders.userTask((f) -> f.candidateUsers("candidateUser1"));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(candidateUsersFilter));

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
                        assertThat(term.field()).isEqualTo("candidateUsers");
                        assertThat(term.value().stringValue()).isEqualTo("candidateUser1");
                      });
            });
  }

  @Test
  public void shouldQueryByCandidateGroups() {
    // given
    final var candidateGroupsFilter =
        FilterBuilders.userTask((f) -> f.candidateGroups("candidateGroup1"));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(candidateGroupsFilter));

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
                        assertThat(term.field()).isEqualTo("candidateGroups");
                        assertThat(term.value().stringValue()).isEqualTo("candidateGroup1");
                      });
            });
  }

  @Nested
  class PriorityFilters {
    private static Stream<Arguments> providePriorityFilters() {
      return Stream.of(
          Arguments.of(new ComparableValueFilter.Builder().eq(100).build(), SearchBoolQuery.class),
          Arguments.of(new ComparableValueFilter.Builder().neq(10).build(), SearchBoolQuery.class),
          Arguments.of(new ComparableValueFilter.Builder().gt(10).build(), SearchRangeQuery.class),
          Arguments.of(new ComparableValueFilter.Builder().gte(10).build(), SearchRangeQuery.class),
          Arguments.of(new ComparableValueFilter.Builder().lt(100).build(), SearchRangeQuery.class),
          Arguments.of(
              new ComparableValueFilter.Builder().lte(100).build(), SearchRangeQuery.class),
          Arguments.of(
              new ComparableValueFilter.Builder().gt(20).lt(100).build(), SearchRangeQuery.class),
          Arguments.of(
              new ComparableValueFilter.Builder().gte(20).lte(100).build(),
              SearchRangeQuery.class));
    }

    @ParameterizedTest
    @MethodSource("providePriorityFilters")
    public void shouldQueryByPriority(
        final ComparableValueFilter filter, final Class<? extends SearchQueryOption> queryClass) {
      // given
      final var priorityFilter = FilterBuilders.userTask().priority(filter);
      final var searchQuery =
          SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(priorityFilter.build()));

      // when
      services.search(searchQuery);

      // then
      final var searchRequest = client.getSingleSearchRequest();
      final var queryVariant = searchRequest.query().queryOption();

      assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
      assertThat(queryVariant)
          .isInstanceOfSatisfying(
              SearchBoolQuery.class,
              (t) ->
                  assertThat(t.must().get(1).queryOption())
                      .isInstanceOfSatisfying(
                          queryClass,
                          (term) -> {
                            switch (term) {
                              case final SearchRangeQuery rangeQuery -> {
                                assertThat(rangeQuery.field()).isEqualTo("priority");
                                assertThat(rangeQuery.gt()).isEqualTo(filter.gt());
                                assertThat(rangeQuery.gte()).isEqualTo(filter.gte());
                                assertThat(rangeQuery.lt()).isEqualTo(filter.lt());
                                assertThat(rangeQuery.lte()).isEqualTo(filter.lte());
                              }
                              case final SearchBoolQuery boolQuery -> {
                                if (filter.eq() != null) {
                                  assertThat(boolQuery.must().getFirst().queryOption())
                                      .isInstanceOf(SearchTermQuery.class);
                                  assertThat(
                                          ((SearchTermQuery)
                                                  boolQuery.must().getFirst().queryOption())
                                              .value()
                                              .intValue())
                                      .isEqualTo(filter.eq());
                                }
                                if (filter.neq() != null) {
                                  assertThat(boolQuery.mustNot().getFirst().queryOption())
                                      .isInstanceOf(SearchTermQuery.class);
                                  assertThat(
                                          ((SearchTermQuery)
                                                  boolQuery.mustNot().getFirst().queryOption())
                                              .value()
                                              .intValue())
                                      .isEqualTo(filter.neq());
                                }
                              }
                              default ->
                                  throw new IllegalArgumentException(
                                      "Unexpected query class: " + queryClass);
                            }
                          }));
    }
  }
}

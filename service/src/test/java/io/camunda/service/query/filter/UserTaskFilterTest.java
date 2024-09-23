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
import io.camunda.search.clients.query.SearchExistsQuery;
import io.camunda.search.clients.query.SearchHasChildQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.sort.SortOrder;
import io.camunda.service.UserTaskServices;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.UserTaskFilter.Builder;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
            SearchExistsQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("flowNodeInstanceId"); // Retrieve only User Task
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
    final var userTaskStateFilter = FilterBuilders.userTask((f) -> f.states("CREATED"));
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
    final var userTaskStateFilter = FilterBuilders.userTask((f) -> f.states("CREATED"));
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
    final var taskStateFilter = FilterBuilders.userTask((f) -> f.states("CREATED"));
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

  @Test
  public void shouldReturnSingleUserTask() {
    // when
    final var searchQueryResult = services.getByKey(1L);

    // then
    assertThat(searchQueryResult.key()).isEqualTo(123L);
  }

  public void shouldQueryByVariableValueFilter() {
    // given
    final VariableValueFilter.Builder variableValueFilterBuilder =
        new VariableValueFilter.Builder();
    variableValueFilterBuilder.name("test").eq("test").build();

    final VariableValueFilter variableFilterValue = variableValueFilterBuilder.build();

    final var variableValueFilter =
        FilterBuilders.userTask((f) -> f.variable(List.of(variableFilterValue)));
    final var searchQuery =
        SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(variableValueFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();
    final var queryVariant = searchRequest.query().queryOption();

    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            outerBoolQuery -> {
              assertThat(outerBoolQuery.must()).isNotEmpty();

              final SearchQuery outerMustQuery = outerBoolQuery.must().get(0);
              assertThat(outerMustQuery.queryOption()).isInstanceOf(SearchBoolQuery.class);

              // Drill down into the nested SearchBoolQuery
              final SearchBoolQuery nestedBoolQuery =
                  (SearchBoolQuery) outerMustQuery.queryOption();
              assertThat(nestedBoolQuery.should()).isNotEmpty();

              final SearchQuery shouldQuery = nestedBoolQuery.should().get(0);
              assertThat(shouldQuery.queryOption()).isInstanceOf(SearchHasChildQuery.class);

              final SearchHasChildQuery childQuery =
                  (SearchHasChildQuery) shouldQuery.queryOption();
              assertThat(childQuery.type()).isEqualTo("taskVariable");

              // Check the inner bool query inside the child query
              final SearchQuery innerQuery = childQuery.query();
              assertThat(innerQuery.queryOption()).isInstanceOf(SearchBoolQuery.class);

              final SearchBoolQuery innerBoolQuery = (SearchBoolQuery) innerQuery.queryOption();
              assertThat(innerBoolQuery.must()).hasSize(2);

              assertThat(innerBoolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      termQuery -> {
                        assertThat(termQuery.field()).isEqualTo("name");
                        assertThat(termQuery.value().value()).isEqualTo("test");
                      });

              assertThat(innerBoolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      termQuery -> {
                        assertThat(termQuery.field()).isEqualTo("value");
                        assertThat(termQuery.value().value()).isEqualTo("test");
                      });
            });
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchHasChildQuery;
import io.camunda.search.clients.query.SearchMatchQuery;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.SearchQueryBuilders;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryWhenEmpty() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f);

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertIsSearchTermQuery(queryVariant, "joinRelation", "processInstance");
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processInstanceKeys(123L));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "key", 123L);
  }

  @Test
  public void shouldQueryByProcessDefinitionId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processDefinitionIds("bpmn"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "bpmnProcessId", "bpmn");
  }

  @Test
  public void shouldQueryByProcessDefinitionName() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processDefinitionNames("Demo Process"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(),
        "processName",
        "Demo Process");
  }

  @Test
  public void shouldQueryByProcessDefinitionVersion() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processDefinitionVersions(33));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "processVersion", 33);
  }

  @Test
  public void shouldQueryByProcessDefinitionVersionTag() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processDefinitionVersionTags("v1"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "processVersionTag", "v1");
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processDefinitionKeys(567L));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "processDefinitionKey", 567L);
  }

  @Test
  public void shouldQueryByParentProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.parentProcessInstanceKeys(567L));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(),
        "parentProcessInstanceKey",
        567L);
  }

  @Test
  public void shouldQueryByParentFlowNodeInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.parentFlowNodeInstanceKeys(567L));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(),
        "parentFlowNodeInstanceKey",
        567L);
  }

  @Test
  public void shouldQueryByStartDateAndEndDate() {
    // given
    final var dateAfter = OffsetDateTime.of(2024, 3, 12, 10, 30, 15, 0, ZoneOffset.UTC);
    final var dateBefore = OffsetDateTime.of(2024, 7, 15, 10, 30, 15, 0, ZoneOffset.UTC);
    final var dateFilter = List.of(Operation.gte(dateAfter), Operation.lt(dateBefore));
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            f -> f.startDateOperations(dateFilter).endDateOperations(dateFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            (searchRangeQuery) -> {
              assertThat(searchRangeQuery.field()).isEqualTo("startDate");
              assertThat(searchRangeQuery.gte()).isEqualTo("2024-03-12T10:30:15.000+0000");
              assertThat(searchRangeQuery.lt()).isEqualTo("2024-07-15T10:30:15.000+0000");
              assertThat(searchRangeQuery.format()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            (searchRangeQuery) -> {
              assertThat(searchRangeQuery.field()).isEqualTo("endDate");
              assertThat(searchRangeQuery.gte()).isEqualTo("2024-03-12T10:30:15.000+0000");
              assertThat(searchRangeQuery.lt()).isEqualTo("2024-07-15T10:30:15.000+0000");
              assertThat(searchRangeQuery.format()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
            });
  }

  @Test
  public void shouldQueryByState() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.states("ACTIVE"));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "state", "ACTIVE");
  }

  @Test
  public void shouldQueryByIncident() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.hasIncident(true));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "incident", true);
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.tenantIds("tenant"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "tenantId", "tenant");
  }

  @Test
  public void shouldQueryByBatchOperationId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            f -> f.batchOperationIds("ab1db89e-4822-4330-90b5-b98346f8f83a"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(),
        "batchOperationIds",
        "ab1db89e-4822-4330-90b5-b98346f8f83a");
  }

  @Test
  public void shouldQueryByErrorMessage() {
    // given
    final String expectedError = "expected error";
    final ProcessInstanceFilter filter =
        FilterBuilders.processInstance(
            f -> f.errorMessageOperations(List.of(Operation.eq(expectedError))));

    // when: transform the filter into a SearchQuery
    final var searchRequest = transformQuery(filter);

    // then: the overall query should be a SearchBoolQuery with two must clauses.
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final SearchBoolQuery boolQuery = (SearchBoolQuery) queryVariant;

    // Expect two must clauses: one for joinRelation and one for errorMessage.
    assertThat(boolQuery.must()).hasSize(2);

    // First must clause: joinRelation term query.
    assertIsSearchTermQuery(
        boolQuery.must().get(0).queryOption(), "joinRelation", "processInstance");

    // Second must clause: should be a has_child query for errorMessage.
    assertThat(boolQuery.must().get(1).queryOption())
        .isInstanceOf(SearchHasChildQuery.class)
        .satisfies(
            queryOption -> {
              // Cast the queryOption to SearchHasChildQuery
              final SearchHasChildQuery hasChildQuery = (SearchHasChildQuery) queryOption;
              assertThat(hasChildQuery.type()).isEqualTo("activity");
              // Assert that the inner query is a match query on "errorMessage" with our
              // expected value.
              assertThat(hasChildQuery.query().queryOption())
                  .isInstanceOfSatisfying(
                      SearchMatchQuery.class,
                      (searchMatchQuery) -> {
                        assertThat(searchMatchQuery.field()).isEqualTo("errorMessage");
                        assertThat(searchMatchQuery.query()).isEqualTo(expectedError);
                      });
            });
  }

  @Test
  public void shouldQueryByHasRetriesLeft() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.hasRetriesLeft(true));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().getFirst().queryOption(),
        "joinRelation",
        "processInstance");

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchHasChildQuery.class,
            (searchHasChildQuery) -> {
              assertIsSearchTermQuery(
                  searchHasChildQuery.query().queryOption(), "jobFailedWithRetriesLeft", true);
            });
  }

  @Test
  public void shouldQueryByFlowNodeId() {
    // when
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            b -> b.processDefinitionKeys(123L).flowNodeIds("activity_123"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "processDefinitionKey", 123L);

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchHasChildQuery.class,
            hasChildQuery -> {
              assertIsSearchTermQuery(
                  (hasChildQuery.query().queryOption()), "activityId", "activity_123");
              assertThat(hasChildQuery.type()).isEqualTo("activity");
            });
  }

  @Test
  public void shouldQueryByFlowNodeIdAndFlowNodeInstanceState() {
    // when
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            b ->
                b.processDefinitionKeys(123L)
                    .flowNodeIds("activity_123")
                    .flowNodeInstanceState("ACTIVE", "COMPLETED"));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "processDefinitionKey", 123L);

    assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchHasChildQuery.class,
            hasChildQuery -> {
              assertThat(hasChildQuery.query().queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      boolQuery -> {
                        assertIsSearchTermQuery(
                            boolQuery.must().getFirst().queryOption(),
                            "activityId",
                            "activity_123");
                        assertThat(boolQuery.must().get(1).queryOption())
                            .isInstanceOfSatisfying(
                                SearchTermsQuery.class,
                                termsQuery -> {
                                  assertThat(termsQuery.field()).isEqualTo("activityState");
                                  assertThat(termsQuery.values()).hasSize(2);
                                  assertThat(termsQuery.values())
                                      .extracting("value")
                                      .containsExactly("ACTIVE", "COMPLETED");
                                });
                      });

              assertThat(hasChildQuery.type()).isEqualTo("activity");
            });
  }

  @Test
  public void shouldQueryByFlowNodeIdAndFlowNodeInstanceStateAndHasIncident() {
    {
      // when
      final var processInstanceFilter =
          FilterBuilders.processInstance(
              b ->
                  b.processDefinitionKeys(123L)
                      .flowNodeIds("activity_123")
                      .flowNodeInstanceState("ACTIVE", "COMPLETED")
                      .hasFlowNodeInstanceIncident(true));

      // when
      final var searchRequest = transformQuery(processInstanceFilter);

      // then
      final var queryVariant = searchRequest.queryOption();
      assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
      assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(3);
      assertIsSearchTermQuery(
          ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
          "joinRelation",
          "processInstance");
      assertIsSearchTermQuery(
          ((SearchBoolQuery) queryVariant).must().get(1).queryOption(),
          "processDefinitionKey",
          123L);

      assertThat(((SearchBoolQuery) queryVariant).must().get(2).queryOption())
          .isInstanceOfSatisfying(
              SearchHasChildQuery.class,
              hasChildQuery -> {
                assertThat(hasChildQuery.query().queryOption())
                    .isInstanceOfSatisfying(
                        SearchBoolQuery.class,
                        boolQuery -> {
                          assertIsSearchTermQuery(
                              boolQuery.must().getFirst().queryOption(),
                              "activityId",
                              "activity_123");
                          assertThat(boolQuery.must().get(1).queryOption())
                              .isInstanceOfSatisfying(
                                  SearchTermsQuery.class,
                                  termsQuery -> {
                                    assertThat(termsQuery.field()).isEqualTo("activityState");
                                    assertThat(termsQuery.values()).hasSize(2);
                                    assertThat(termsQuery.values())
                                        .extracting("value")
                                        .containsExactly("ACTIVE", "COMPLETED");
                                  });
                          assertIsSearchTermQuery(
                              boolQuery.must().get(2).queryOption(), "incident", true);
                        });

                assertThat(hasChildQuery.type()).isEqualTo("activity");
              });
    }
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = (new ProcessInstanceFilter.Builder()).build();

    // then
    assertThat(processInstanceFilter.processInstanceKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionIdOperations()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionNameOperations()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionVersionOperations()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionVersionTagOperations()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.parentProcessInstanceKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.parentFlowNodeInstanceKeyOperations()).isEmpty();
    assertThat(processInstanceFilter.startDateOperations()).isEmpty();
    assertThat(processInstanceFilter.endDateOperations()).isEmpty();
    assertThat(processInstanceFilter.stateOperations()).isEmpty();
    assertThat(processInstanceFilter.hasIncident()).isNull();
    assertThat(processInstanceFilter.tenantIdOperations()).isEmpty();
    assertThat(processInstanceFilter.errorMessageOperations()).isEmpty();
  }

  @Test
  public void shouldQueryWithOrConditions() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(
            f ->
                f.tenantIds("tenant-1")
                    .addOrOperation(
                        new ProcessInstanceFilter.Builder()
                            .states("ACTIVE")
                            .hasIncident(true)
                            .build())
                    .addOrOperation(
                        new ProcessInstanceFilter.Builder()
                            .states("COMPLETED")
                            .hasIncident(false)
                            .build()));

    // when
    final var searchRequest = transformQuery(processInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final SearchBoolQuery outerBool = (SearchBoolQuery) queryVariant;

    // Validate the must clause contains tenantId and the OR conditions
    assertThat(outerBool.must()).hasSize(3);
    assertIsSearchTermQuery(
        outerBool.must().get(0).queryOption(), "joinRelation", "processInstance");
    assertIsSearchTermQuery(outerBool.must().get(1).queryOption(), "tenantId", "tenant-1");

    assertThat(outerBool.must().get(2).queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            orBool -> {
              assertThat(orBool.should()).hasSize(2);
              assertThat(orBool.should().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      orBool2 -> {
                        assertIsSearchTermQuery(
                            orBool2.must().get(0).queryOption(), "state", "ACTIVE");
                        assertIsSearchTermQuery(
                            orBool2.must().get(1).queryOption(), "incident", true);
                      });
              assertThat(orBool.should().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchBoolQuery.class,
                      orBool3 -> {
                        assertIsSearchTermQuery(
                            orBool3.must().get(0).queryOption(), "state", "COMPLETED");
                        assertIsSearchTermQuery(
                            orBool3.must().get(1).queryOption(), "incident", false);
                      });
            });
  }

  private void assertIsSearchTermQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final String expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().stringValue()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchMatchQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final String expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchMatchQuery.class,
            (searchMatchQuery) -> {
              assertThat(searchMatchQuery.field()).isEqualTo(expectedField);
              assertThat(searchMatchQuery.query()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchTermQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final Long expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().longValue()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchTermQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final Integer expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().intValue()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchTermQuery(
      final SearchQueryOption searchQueryOption,
      final String expectedField,
      final Boolean expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().booleanValue()).isEqualTo(expectedValue);
            });
  }
}

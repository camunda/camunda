/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.*;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.ProcessInstanceFilter;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceFilterTest {

  private ProcessInstanceServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessInstanceSearchQueryStub().registerWith(client);
    services = new ProcessInstanceServices(null, client);
  }

  @Test
  public void shouldQueryWhenEmpty() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f);
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertIsSearchTermQuery(queryVariant, "joinRelation", "processInstance");
  }

  @Test
  public void shouldQueryByKey() {
    // given
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.keys(List.of(123L)));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldQueryByBpmnProcessId() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.bpmnProcessIds(List.of("bpmn")));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldQueryByProcessName() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processNames(List.of("Demo Process")));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldQueryByProcessVersion() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processVersions(List.of(33)));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldQueryByProcessVersionTag() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.processVersionTags(List.of("v1")));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
        FilterBuilders.processInstance(f -> f.processDefinitionKeys(List.of(567L)));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldQueryByRootProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.rootProcessInstanceKeys(List.of(567L)));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(),
        "rootProcessInstanceKey",
        567L);
  }

  @Test
  public void shouldQueryByParentProcessInstanceKey() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.parentProcessInstanceKeys(List.of(567L)));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
        FilterBuilders.processInstance(f -> f.parentFlowNodeInstanceKeys(List.of(567L)));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldQueryByTreePath() {
    // given
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.treePaths(List.of("PI_12")));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(0).queryOption(),
        "joinRelation",
        "processInstance");
    assertIsSearchTermQuery(
        ((SearchBoolQuery) queryVariant).must().get(1).queryOption(), "treePath", "PI_12");
  }

  @Test
  public void shouldQueryByStartDateAndEndDate() {
    // given
    final var dateAfter = OffsetDateTime.of(2024, 3, 12, 10, 30, 15, 0, ZoneOffset.UTC);
    final var dateBefore = OffsetDateTime.of(2024, 7, 15, 10, 30, 15, 0, ZoneOffset.UTC);
    final var startDateFilter =
        FilterBuilders.dateValue((d) -> d.after(dateAfter).before(dateBefore));
    final var endDateFilter =
        FilterBuilders.dateValue((d) -> d.after(dateAfter).before(dateBefore));
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.startDate(startDateFilter).endDate(endDateFilter));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.states(List.of("ACTIVE")));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var processInstanceFilter = FilterBuilders.processInstance(f -> f.incident(true));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
    final var processInstanceFilter =
        FilterBuilders.processInstance(f -> f.tenantIds(List.of("tenant")));
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(q -> q.filter(processInstanceFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
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
  public void shouldReturnProcessInstance() {
    // given
    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final ProcessInstanceEntity item = searchQueryResult.items().get(0);
    assertThat(item.key()).isEqualTo(123L);
  }

  @Test
  public void shouldCreateDefaultFilter() {
    // given

    // when
    final var processInstanceFilter = (new ProcessInstanceFilter.Builder()).build();

    // then
    assertThat(processInstanceFilter.keys()).isEmpty();
    assertThat(processInstanceFilter.bpmnProcessIds()).isEmpty();
    assertThat(processInstanceFilter.processNames()).isEmpty();
    assertThat(processInstanceFilter.processVersions()).isEmpty();
    assertThat(processInstanceFilter.processVersionTags()).isEmpty();
    assertThat(processInstanceFilter.processDefinitionKeys()).isEmpty();
    assertThat(processInstanceFilter.rootProcessInstanceKeys()).isEmpty();
    assertThat(processInstanceFilter.parentProcessInstanceKeys()).isEmpty();
    assertThat(processInstanceFilter.parentFlowNodeInstanceKeys()).isEmpty();
    assertThat(processInstanceFilter.treePaths()).isEmpty();
    assertThat(processInstanceFilter.startDate()).isNull();
    assertThat(processInstanceFilter.endDate()).isNull();
    assertThat(processInstanceFilter.states()).isEmpty();
    assertThat(processInstanceFilter.incident()).isNull();
    assertThat(processInstanceFilter.tenantIds()).isEmpty();
  }

  private void assertIsSearchTermQuery(
      SearchQueryOption searchQueryOption, String expectedField, String expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().stringValue()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchTermQuery(
      SearchQueryOption searchQueryOption, String expectedField, Long expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().longValue()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchTermQuery(
      SearchQueryOption searchQueryOption, String expectedField, Integer expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().intValue()).isEqualTo(expectedValue);
            });
  }

  private void assertIsSearchTermQuery(
      SearchQueryOption searchQueryOption, String expectedField, Boolean expectedValue) {
    assertThat(searchQueryOption)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (searchTermQuery) -> {
              assertThat(searchTermQuery.field()).isEqualTo(expectedField);
              assertThat(searchTermQuery.value().booleanValue()).isEqualTo(expectedValue);
            });
  }
}

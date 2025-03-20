/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.IncidentServices;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.entities.IncidentEntity.ErrorType;
import io.camunda.service.entities.IncidentEntity.IncidentState;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public final class IncidentFilterTest {

  private IncidentServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new IncidentSearchQueryStub().registerWith(client);
    services = new IncidentServices(null, client, null, null);
  }

  @Test
  public void shouldReturnIncident() {
    // given
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final IncidentEntity item = searchQueryResult.items().getFirst();
    assertThat(item.key()).isEqualTo(1L);
  }

  @Test
  public void shouldQueryByIncidentKey() {
    final var filter = FilterBuilders.incident(f -> f.keys(1L));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(1L);
            });
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    final var filter = FilterBuilders.incident(f -> f.processDefinitionKeys(5432L));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processDefinitionKey");
              assertThat(t.value().longValue()).isEqualTo(5432L);
            });
  }

  @Test
  public void shouldQueryByBpmnProcessId() {
    final var filter = FilterBuilders.incident(f -> f.bpmnProcessIds("complexProcess"));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("bpmnProcessId");
              assertThat(t.value().stringValue()).isEqualTo("complexProcess");
            });
  }

  @Test
  public void shouldQueryByProcessInstanceKey() {
    final var filter = FilterBuilders.incident(f -> f.processInstanceKeys(42L));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("processInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(42L);
            });
  }

  @ParameterizedTest
  @EnumSource(io.camunda.zeebe.protocol.record.value.ErrorType.class)
  public void shouldQueryByErrorType(
      final io.camunda.zeebe.protocol.record.value.ErrorType errorType) {
    // given
    final var serviceErrorType = ErrorType.valueOf(errorType.name());
    final var filter = FilterBuilders.incident(f -> f.errorTypes(serviceErrorType));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorType");
              assertThat(t.value().stringValue()).isEqualTo(serviceErrorType.name());
            });
  }

  @Test
  public void shouldQueryByErrorMessage() {
    final var filter = FilterBuilders.incident(f -> f.errorMessages("No retries left."));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("errorMessage");
              assertThat(t.value().stringValue()).isEqualTo("No retries left.");
            });
  }

  @Test
  public void shouldQueryByFlowNodeId() {
    final var filter = FilterBuilders.incident(f -> f.flowNodeIds("flowNodeId-17"));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeId");
              assertThat(t.value().stringValue()).isEqualTo("flowNodeId-17");
            });
  }

  @Test
  public void shouldQueryByFlowNodeInstanceKey() {
    final var filter = FilterBuilders.incident(f -> f.flowNodeInstanceKeys(42L));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("flowNodeInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(42L);
            });
  }

  @Test
  public void shouldQueryByCreationTime() {
    final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    final var date = OffsetDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
    final var dateFilter = new DateValueFilter.Builder().before(date).after(date).build();
    final var filter = FilterBuilders.incident(f -> f.creationTime(dateFilter));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("creationTime");
              assertThat(t.gte().toString()).isEqualTo(date.format(formatter));
            });
  }

  @Test
  public void shouldQueryByState() {
    final var filter = FilterBuilders.incident(f -> f.states(IncidentState.ACTIVE));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("ACTIVE");
            });
  }

  @Test
  public void shouldQueryByJobKey() {
    final var filter = FilterBuilders.incident(f -> f.jobKeys(23L));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("jobKey");
              assertThat(t.value().longValue()).isEqualTo(23L);
            });
  }

  @Test
  public void shouldQueryByTreePath() {
    final var filter = FilterBuilders.incident(f -> f.treePaths("/"));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("treePath");
              assertThat(t.value().stringValue()).isEqualTo("/");
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    final var filter = FilterBuilders.incident(f -> f.tenantIds("Homer"));
    final var searchQuery = SearchQueryBuilders.incidentSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("Homer");
            });
  }
}

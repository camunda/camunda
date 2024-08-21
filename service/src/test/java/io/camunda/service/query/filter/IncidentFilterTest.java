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
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.IncidentFilter.Builder;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  public void shouldQueryByState() {
    final var filter = FilterBuilders.incident(f -> f.states("COMPLETED"));
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
              assertThat(t.value().stringValue()).isEqualTo("COMPLETED");
            });
  }

  @Test
  public void shouldQueryByCreationTime() {
    final var creationTime = OffsetDateTime.of(2024, 5, 23, 23, 5, 0, 0, ZoneOffset.UTC);
    final var creationTimeFilter =
        FilterBuilders.dateValue((d) -> d.after(creationTime).before(creationTime));
    final var filter = FilterBuilders.incident(f -> f.creationTime(creationTimeFilter));
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
              assertThat(t.gte()).isEqualTo("2024-05-23T23:05:00.000+0000");
              assertThat(t.lt()).isEqualTo("2024-05-23T23:05:00.000+0000");
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
  public void shouldQueryByType() {
    final var filter = FilterBuilders.incident(f -> f.types("ERROR"));
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
              assertThat(t.field()).isEqualTo("type");
              assertThat(t.value().stringValue()).isEqualTo("ERROR");
            });
  }

  @Test
  public void shouldQueryByHasActiveOperation() {
    final var filter = FilterBuilders.incident(Builder::hasActiveOperation);
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
              assertThat(t.field()).isEqualTo("hasActiveOperation");
              assertThat(t.value().booleanValue()).isEqualTo(true);
            });
  }

  @Test
  public void shouldQueryByFlowNodeInstanceId() {
    final var filter = FilterBuilders.incident(f -> f.flowNodeInstanceIds("flowNodeInstance-23"));
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
              assertThat(t.field()).isEqualTo("flowNodeInstanceId");
              assertThat(t.value().stringValue()).isEqualTo("flowNodeInstance-23");
            });
  }
}

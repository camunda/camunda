/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.entities.ProcessDefinitionEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedCamundaSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessDefinitionFilterTest {

  private ProcessDefinitionServices services;
  private StubbedCamundaSearchClient client;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new ProcessDefinitionSearchQueryStub().registerWith(client);
    services = new ProcessDefinitionServices(null, client, null, null);
  }

  @Test
  public void shouldReturnProcessDefinition() {
    // given
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(1);
    assertThat(searchQueryResult.items()).hasSize(1);
    final ProcessDefinitionEntity item = searchQueryResult.items().getFirst();
    assertThat(item.key()).isEqualTo(1L);
  }

  @Test
  public void shouldQueryByProcessDefinitionKey() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.processDefinitionKeys(1L));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

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
  public void shouldQueryByName() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.names("processName"));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("processName");
            });
  }

  @Test
  public void shouldQueryByBpmnProcessId() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.bpmnProcessIds("complexProcess"));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

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
  public void shouldQueryByResourceName() {
    // given
    final var filter =
        FilterBuilders.processDefinition(f -> f.resourceNames("complexProcess.bpmn"));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("resourceName");
              assertThat(t.value().stringValue()).isEqualTo("complexProcess.bpmn");
            });
  }

  @Test
  public void shouldQueryByVersion() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.versions(5));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("version");
              assertThat(t.value().intValue()).isEqualTo(5);
            });
  }

  @Test
  public void shouldQueryByVersionTag() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.versionTags("alpha"));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("versionTag");
              assertThat(t.value().stringValue()).isEqualTo("alpha");
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var filter = FilterBuilders.processDefinition(f -> f.tenantIds("<default>"));
    final var searchQuery = SearchQueryBuilders.processDefinitionSearchQuery(q -> q.filter(filter));

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
              assertThat(t.value().stringValue()).isEqualTo("<default>");
            });
  }
}

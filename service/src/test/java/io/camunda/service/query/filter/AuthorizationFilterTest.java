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
import io.camunda.service.AuthorizationServices;
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.search.filter.AuthorizationFilter;
import io.camunda.service.search.filter.AuthorizationFilter.Builder;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AuthorizationFilterTest {
  private AuthorizationServices<AuthorizationRecord> services;
  private StubbedCamundaSearchClient client;
  private StubbedBrokerClient brokerClient;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new AuthorizationSearchQueryStub().registerWith(client);
    services = new AuthorizationServices<>(brokerClient, null, null);
  }

  @Test
  public void emptyQueryReturnsAllResults() {
    // given
    final AuthorizationFilter filter = new AuthorizationFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.authorizationSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(2);
    assertThat(searchQueryResult.items()).hasSize(2);
    final AuthorizationEntity item = searchQueryResult.items().get(0);
    assertThat(item.value().ownerKey()).isEqualTo("username1");
  }

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<AuthorizationFilter>> fn,
      final String column,
      final String value) {
    // given
    final var authorizationFilter = FilterBuilders.authorization(fn);
    final var searchQuery =
        SearchQueryBuilders.authorizationSearchQuery(q -> q.filter(authorizationFilter));

    // when
    services.search(searchQuery);

    // then
    final var searchRequest = client.getSingleSearchRequest();

    final var queryVariant = searchRequest.query().queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(column);
              assertThat(t.value().stringValue()).isEqualTo(value);
            });
  }

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>) f -> f.ownerKey("username1"),
            "value.ownerKey",
            "username1"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>) f -> f.ownerType("user"),
            "value.ownerType",
            "user"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>)
                f -> f.resourceKey("bpmnProcessId:456"),
            "value.resourceKey",
            "bpmnProcessId:456"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>)
                f -> f.resourceType("process-definition"),
            "value.resourceType",
            "process-definition"));
  }
}

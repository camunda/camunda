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
import io.camunda.service.UserServices;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.search.filter.FilterBuilders;
import io.camunda.service.search.filter.UserFilter;
import io.camunda.service.search.filter.UserFilter.Builder;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.util.StubbedBrokerClient;
import io.camunda.service.util.StubbedCamundaSearchClient;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UserFilterTest {
  private UserServices services;
  private StubbedCamundaSearchClient client;
  private StubbedBrokerClient brokerClient;

  @BeforeEach
  public void before() {
    client = new StubbedCamundaSearchClient();
    new UserSearchQueryStub().registerWith(client);
    services = new UserServices(brokerClient, null, null);
  }

  @Test
  public void shouldEmptyQueryReturnUsers() {
    // given
    final UserFilter filter = new UserFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.userSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult.total()).isEqualTo(2);
    assertThat(searchQueryResult.items()).hasSize(2);
    final UserEntity item = searchQueryResult.items().get(0);
    assertThat(item.name()).isEqualTo("name1");
  }

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<UserFilter>> fn,
      final String column,
      final Object value) {
    // given
    final var userFilter = FilterBuilders.user(fn);
    final var searchQuery = SearchQueryBuilders.userSearchQuery(q -> q.filter(userFilter));

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
              assertThat(t.value().value()).isEqualTo(value);
            });
  }

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of((Function<Builder, ObjectBuilder<UserFilter>>) f -> f.key(1L), "key", 1L),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.username("username1"),
            "username",
            "username1"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.name("name1"), "name", "name1"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.email("email1"),
            "email",
            "email1"));
  }
}

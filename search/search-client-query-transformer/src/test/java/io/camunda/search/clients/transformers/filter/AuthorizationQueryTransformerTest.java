/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.AuthorizationFilter.Builder;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class AuthorizationQueryTransformerTest extends AbstractTransformerTest {

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<AuthorizationFilter>> fn,
      final String column,
      final Object value) {
    // given
    final var filter = FilterBuilders.authorization(fn);

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
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
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>) f -> f.ownerIds("foo"),
            "ownerId",
            "foo"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>) f -> f.ownerType("user"),
            "ownerType",
            "user"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>) f -> f.resourceIds("456"),
            "resourceId",
            "456"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>)
                f -> f.resourceType("process-definition"),
            "resourceType",
            "process-definition"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<AuthorizationFilter>>)
                f -> f.permissionTypes(PermissionType.READ),
            "permissionTypes",
            PermissionType.READ.name()));
  }
}

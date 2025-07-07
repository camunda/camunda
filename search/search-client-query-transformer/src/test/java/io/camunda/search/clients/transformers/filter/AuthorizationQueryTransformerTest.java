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
import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.AuthorizationFilter.Builder;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationResult;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.security.resource.TenantResult;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
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

  @Test
  public void shouldApplyAuthorizationFilterWithResourceIds() {
    // given
    final var filter = FilterBuilders.authorization(b -> b);
    final var expectedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(AuthorizationResourceType.AUTHORIZATION)
                    .permissionType(PermissionType.READ)
                    .resourceIds(List.of("123")));
    final var authorizationFilter =
        AuthorizationResult.requiredAuthorizationCheck(expectedAuthorization);

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.authorizationResult(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("id");
              assertThat(t.value().value()).isEqualTo("123");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithGranted() {
    // given
    final var filter = FilterBuilders.authorization(b -> b);
    final var authorizationFilter = AuthorizationResult.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.authorizationResult(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyAuthorizationFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.authorization(b -> b);
    final var authorizationFilter = AuthorizationResult.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.authorizationResult(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithGranted() {
    // given
    final var filter = FilterBuilders.authorization(b -> b);
    final var tenantFilter = TenantResult.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.authorization(b -> b);
    final var tenantFilter = TenantResult.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreTenantFilterWithTenantIds() {
    // given
    final var filter = FilterBuilders.authorization(b -> b);
    final var tenantFilter = TenantResult.tenantCheckRequired(List.of("bar"));

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessResult.of(b -> b.tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyAllFilters() {
    // given
    final var filter = FilterBuilders.authorization(b -> b.ownerType("foo"));
    final var authorizationFilter = AuthorizationResult.successful();
    final var tenantFilter = TenantResult.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter,
            ResourceAccessResult.of(
                b -> b.authorizationResult(authorizationFilter).tenantResult(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var must = ((SearchBoolQuery) queryVariant).must();
    assertThat(must).hasSize(3);
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

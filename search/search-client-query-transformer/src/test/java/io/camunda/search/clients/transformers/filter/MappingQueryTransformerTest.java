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
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.filter.MappingFilter.Builder;
import io.camunda.search.filter.MappingFilter.Claim;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationResult;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.security.resource.TenantResult;
import io.camunda.util.ObjectBuilder;
import io.camunda.webapps.schema.descriptors.index.MappingIndex;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MappingQueryTransformerTest extends AbstractTransformerTest {

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<MappingFilter>> fn, final SearchQuery expected) {
    // given
    final var filter = FilterBuilders.mapping(fn);
    // when
    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest).isEqualTo(expected);
  }

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>)
                f -> f.claimNames(List.of("foo", "bar")),
            SearchQuery.of(
                q -> q.terms(t -> t.field("claimName").stringTerms(List.of("foo", "bar"))))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>) f -> f.claimName("barfoo"),
            SearchQuery.of(q -> q.term(t -> t.field("claimName").value("barfoo")))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>) f -> f.claimValue("foobar"),
            SearchQuery.of(q -> q.term(t -> t.field("claimValue").value("foobar")))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>) f -> f.name("foobar"),
            SearchQuery.of(q -> q.term(t -> t.field("name").value("foobar")))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>)
                f -> f.mappingIds(Set.of("id1", "id2")),
            SearchQuery.of(
                q ->
                    q.terms(
                        t ->
                            t.field("mappingId")
                                .stringTerms(Set.of("id1", "id2").stream().sorted().toList())))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>)
                f -> f.claims(List.of(new Claim("c1", "v1"), new Claim("c2", "v2"))),
            SearchQueryBuilders.or(
                List.of(
                    SearchQueryBuilders.and(
                        List.of(
                            SearchQueryBuilders.term(MappingIndex.CLAIM_NAME, "c1"),
                            SearchQueryBuilders.term(MappingIndex.CLAIM_VALUE, "v1"))),
                    SearchQueryBuilders.and(
                        List.of(
                            SearchQueryBuilders.term(MappingIndex.CLAIM_NAME, "c2"),
                            SearchQueryBuilders.term(MappingIndex.CLAIM_VALUE, "v2")))))));
  }

  @Test
  public void shouldApplyAuthorizationFilterWithResourceIds() {
    // given
    final var filter = FilterBuilders.mapping(b -> b);
    final var expectedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(AuthorizationResourceType.MAPPING_RULE)
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
              assertThat(t.field()).isEqualTo("mappingId");
              assertThat(t.value().value()).isEqualTo("123");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithGranted() {
    // given
    final var filter = FilterBuilders.mapping(b -> b);
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
    final var filter = FilterBuilders.mapping(b -> b);
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
    final var filter = FilterBuilders.mapping(b -> b);
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
    final var filter = FilterBuilders.mapping(b -> b);
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
    final var filter = FilterBuilders.mapping(b -> b);
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
    final var filter = FilterBuilders.mapping(b -> b.claimName("foo"));
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
}

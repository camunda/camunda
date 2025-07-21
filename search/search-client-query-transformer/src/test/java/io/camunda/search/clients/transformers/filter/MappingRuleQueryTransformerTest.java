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
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.filter.MappingRuleFilter.Builder;
import io.camunda.search.filter.MappingRuleFilter.Claim;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.util.ObjectBuilder;
import io.camunda.webapps.schema.descriptors.index.MappingRuleIndex;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MappingRuleQueryTransformerTest extends AbstractTransformerTest {

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<MappingRuleFilter>> fn, final SearchQuery expected) {
    // given
    final var filter = FilterBuilders.mappingRule(fn);
    // when
    final var searchRequest = transformQuery(filter);

    assertThat(searchRequest).isEqualTo(expected);
  }

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingRuleFilter>>)
                f -> f.claimNames(List.of("foo", "bar")),
            SearchQuery.of(
                q -> q.terms(t -> t.field("claimName").stringTerms(List.of("foo", "bar"))))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingRuleFilter>>) f -> f.claimName("barfoo"),
            SearchQuery.of(q -> q.term(t -> t.field("claimName").value("barfoo")))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingRuleFilter>>) f -> f.claimValue("foobar"),
            SearchQuery.of(q -> q.term(t -> t.field("claimValue").value("foobar")))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingRuleFilter>>) f -> f.name("foobar"),
            SearchQuery.of(q -> q.term(t -> t.field("name").value("foobar")))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingRuleFilter>>)
                f -> f.mappingRuleIds(Set.of("id1", "id2")),
            SearchQuery.of(
                q ->
                    q.terms(
                        t ->
                            t.field("mappingRuleId")
                                .stringTerms(Set.of("id1", "id2").stream().sorted().toList())))),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingRuleFilter>>)
                f -> f.claims(List.of(new Claim("c1", "v1"), new Claim("c2", "v2"))),
            SearchQueryBuilders.or(
                List.of(
                    SearchQueryBuilders.and(
                        List.of(
                            SearchQueryBuilders.term(MappingRuleIndex.CLAIM_NAME, "c1"),
                            SearchQueryBuilders.term(MappingRuleIndex.CLAIM_VALUE, "v1"))),
                    SearchQueryBuilders.and(
                        List.of(
                            SearchQueryBuilders.term(MappingRuleIndex.CLAIM_NAME, "c2"),
                            SearchQueryBuilders.term(MappingRuleIndex.CLAIM_VALUE, "v2")))))));
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        Authorization.of(a -> a.mappingRule().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.mappingRule(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(MappingRuleIndex.MAPPING_RULE_ID);
              assertThat(t.values()).hasSize(2);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization = Authorization.of(a -> a.mappingRule().read());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.mappingRule(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.mappingRule(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldIgnoreTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.mappingRule(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization =
        Authorization.of(a -> a.mappingRule().read().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.mappingRule(b -> b.claimName("a")), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(2));
  }
}

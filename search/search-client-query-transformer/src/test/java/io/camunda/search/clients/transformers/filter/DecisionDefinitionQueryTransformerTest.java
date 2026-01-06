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
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class DecisionDefinitionQueryTransformerTest extends AbstractTransformerTest {

  @Test
  public void shouldQueryByDecisionDefinitionKey() {
    // given
    final var filter = FilterBuilders.decisionDefinition(f -> f.decisionDefinitionKeys(123L));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(123L);
            });
  }

  @Test
  public void shouldQueryByDecisionDefinitionName() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.names("foo"));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByDecisionDefinitionVersion() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.versions(2));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("version");
              assertThat(t.value().intValue()).isEqualTo(2);
            });
  }

  @Test
  public void shouldQueryByDecisionId() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionDefinitionIds("foo"));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionId");
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsId() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionRequirementsIds("567"));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionRequirementsId");
              assertThat(t.value().stringValue()).isEqualTo("567");
            });
  }

  @Test
  public void shouldQueryByDecisionRequirementsKey() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.decisionRequirementsKeys(5678L));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionRequirementsKey");
              assertThat(t.value().longValue()).isEqualTo(5678L);
            });
  }

  @Test
  public void shouldQueryByTenantId() {
    // given
    final var decisionDefinitionFilter = FilterBuilders.decisionDefinition(f -> f.tenantIds("foo"));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });
  }

  @Test
  public void shouldQueryByTenantIdAndDecisionDefinitionName() {
    // given
    final var decisionDefinitionFilter =
        FilterBuilders.decisionDefinition(f -> f.tenantIds("tenant").names("foo"));

    // when
    final var searchRequest = transformQuery(decisionDefinitionFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("name");
              assertThat(t.value().stringValue()).isEqualTo("foo");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("tenant");
            });
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.decisionDefinition().readDecisionDefinition().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.decisionDefinition(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(DecisionIndex.DECISION_ID);
              assertThat(t.values()).hasSize(2);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization =
        Authorization.of(a -> a.decisionDefinition().readDecisionDefinition());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.decisionDefinition(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.decisionDefinition(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldApplyTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.decisionDefinition(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(DecisionIndex.TENANT_ID);
              assertThat(t.values()).hasSize(2);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("a", "b");
            });
  }

  @Test
  public void shouldIgnoreTenantCheckWhenDisabled() {
    // given
    final var tenantCheck = TenantCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.decisionDefinition(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.decisionDefinition().readDecisionDefinition().resourceIds(List.of("1", "2")));

    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.decisionDefinition(b -> b.names("abc")), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(3));
  }
}

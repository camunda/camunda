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
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationBasedResourceAccessFilter;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.security.resource.TenantBasedResourceAccessFilter;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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
  public void shouldApplyAuthorizationFilterWithResourceIds() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b);
    final var expectedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(AuthorizationResourceType.DECISION_DEFINITION)
                    .permissionType(PermissionType.READ)
                    .resourceIds(List.of("123")));
    final var authorizationFilter =
        AuthorizationBasedResourceAccessFilter.requiredAuthorizationCheck(expectedAuthorization);

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.authorizationFilter(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionId");
              assertThat(t.value().value()).isEqualTo("123");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithGranted() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b);
    final var authorizationFilter = AuthorizationBasedResourceAccessFilter.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.authorizationFilter(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyAuthorizationFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b);
    final var authorizationFilter = AuthorizationBasedResourceAccessFilter.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.authorizationFilter(authorizationFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithGranted() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b);
    final var tenantFilter = TenantBasedResourceAccessFilter.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchAllQuery.class);
  }

  @Test
  public void shouldApplyTenantFilterWithForbidden() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b);
    final var tenantFilter = TenantBasedResourceAccessFilter.unsuccessful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreTenantFilterWithTenantIds() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b);
    final var tenantFilter = TenantBasedResourceAccessFilter.tenantCheckRequired(List.of("bar"));

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter, ResourceAccessFilter.of(b -> b.tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().value()).isEqualTo("bar");
            });
  }

  @Test
  public void shouldApplyAllFilters() {
    // given
    final var filter = FilterBuilders.decisionDefinition(b -> b.decisionDefinitionIds("foo"));
    final var authorizationFilter = AuthorizationBasedResourceAccessFilter.successful();
    final var tenantFilter = TenantBasedResourceAccessFilter.successful();

    // when
    final var searchRequest =
        transformQueryWithResourceAccessFilter(
            filter,
            ResourceAccessFilter.of(
                b -> b.authorizationFilter(authorizationFilter).tenantFilter(tenantFilter)));

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    final var must = ((SearchBoolQuery) queryVariant).must();
    assertThat(must).hasSize(3);
  }
}

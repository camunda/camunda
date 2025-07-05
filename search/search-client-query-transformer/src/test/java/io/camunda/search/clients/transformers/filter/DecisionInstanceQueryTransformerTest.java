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
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationBasedResourceAccessFilter;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.security.resource.TenantBasedResourceAccessFilter;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionInstanceQueryTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldQueryByKey() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionInstanceKeys(124L));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("key");
              assertThat(t.value().longValue()).isEqualTo(124L);
            });
  }

  @Test
  void shouldQueryByDecisionVersion() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionVersions(1));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionVersion");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }

  @Test
  void shouldQueryByTenantId() {
    // given
    final var decisionInstanceFilter = FilterBuilders.decisionInstance(f -> f.tenantIds("t"));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("tenantId");
              assertThat(t.value().stringValue()).isEqualTo("t");
            });
  }

  @Test
  void shouldQueryByDmnDecisionId() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionIds("dId"));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionId");
              assertThat(t.value().stringValue()).isEqualTo("dId");
            });
  }

  @Test
  void shouldQueryByDecisionKey() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionKeys(12345L));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionDefinitionId");
              assertThat(t.value().stringValue()).isEqualTo("12345");
            });
  }

  @Test
  void shouldQueryByDecisionInstanceId() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionInstanceIds("12345-1"));
    final var searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery(q -> q.filter(decisionInstanceFilter));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("id");
              assertThat(t.value().stringValue()).isEqualTo("12345-1");
            });
  }

  @Test
  void shouldQueryByDmnDecisionName() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionDefinitionNames("n"));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("decisionName");
              assertThat(t.value().stringValue()).isEqualTo("n");
            });
  }

  @Test
  void shouldQueryByDmnDecisionNameAndDecisionVersion() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(
            f -> f.decisionDefinitionNames("n").decisionDefinitionVersions(1));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchBoolQuery.class);
    assertThat(((SearchBoolQuery) queryVariant).must()).hasSize(2);

    assertThat(((SearchBoolQuery) queryVariant).must().get(0).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("decisionName");
              assertThat(t.value().stringValue()).isEqualTo("n");
            });

    assertThat(((SearchBoolQuery) queryVariant).must().get(1).queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            (t) -> {
              assertThat(t.field()).isEqualTo("decisionVersion");
              assertThat(t.value().intValue()).isEqualTo(1);
            });
  }

  @Test
  void shouldQueryByEvaluationDate() {
    // given
    final var dateAfter = OffsetDateTime.of(LocalDateTime.of(2024, 1, 2, 3, 4, 5), ZoneOffset.UTC);
    final var dateBefore = OffsetDateTime.of(LocalDateTime.of(2024, 2, 3, 4, 5, 6), ZoneOffset.UTC);
    final var dateFilter = List.of(Operation.gte(dateAfter), Operation.lt(dateBefore));
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.evaluationDateOperations(dateFilter));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("evaluationDate");
              assertThat(t.gte()).isEqualTo("2024-01-02T03:04:05.000+0000");
              assertThat(t.lt()).isEqualTo("2024-02-03T04:05:06.000+0000");
            });
  }

  @Test
  public void shouldApplyAuthorizationFilterWithResourceIds() {
    // given
    final var filter = FilterBuilders.decisionInstance(b -> b);
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
    final var filter = FilterBuilders.decisionInstance(b -> b);
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
    final var filter = FilterBuilders.decisionInstance(b -> b);
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
    final var filter = FilterBuilders.decisionInstance(b -> b);
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
    final var filter = FilterBuilders.decisionInstance(b -> b);
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
    final var filter = FilterBuilders.decisionInstance(b -> b);
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
    final var filter = FilterBuilders.decisionInstance(b -> b.decisionDefinitionIds("foo"));
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

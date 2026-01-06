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
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
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
  void shouldQueryByElementInstanceKey() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.flowNodeInstanceKeys(12345L));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("elementInstanceKey");
              assertThat(t.value().longValue()).isEqualTo(12345L);
            });
  }

  @Test
  void shouldQueryByDecisionInstanceId() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.decisionInstanceIds("12345-1"));

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
  void shouldQueryByState() {
    // given
    final var decisionInstanceFilter =
        FilterBuilders.decisionInstance(f -> f.states(DecisionInstanceState.EVALUATED));

    // when
    final var searchRequest = transformQuery(decisionInstanceFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo("state");
              assertThat(t.value().stringValue()).isEqualTo("EVALUATED");
            });
  }

  @Test
  public void shouldApplyAuthorizationCheck() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.decisionDefinition().readDecisionInstance().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.decisionInstance(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(DecisionInstanceTemplate.DECISION_ID);
              assertThat(t.values()).hasSize(2);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var authorization = Authorization.of(a -> a.decisionDefinition().readDecisionInstance());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.decisionInstance(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.decisionInstance(b -> b), resourceAccessChecks);

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
        transformQuery(FilterBuilders.decisionInstance(b -> b), resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(DecisionInstanceTemplate.TENANT_ID);
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
        transformQuery(FilterBuilders.decisionInstance(b -> b), resourceAccessChecks);

    // then
    assertThat(searchQuery).isNull();
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var authorization =
        Authorization.of(
            a -> a.decisionDefinition().readDecisionInstance().resourceIds(List.of("1", "2")));

    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.decisionInstance(b -> b.decisionInstanceIds("abc")),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(SearchBoolQuery.class, t -> assertThat(t.must()).hasSize(3));
  }
}

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
import io.camunda.search.filter.FilterBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public final class UsageMetricsQueryTransformerTest extends AbstractTransformerTest {

  private static Consumer<SearchRangeQuery> assertRangeQuery() {
    return rangeQuery -> {
      assertThat(rangeQuery)
          .extracting("field", "gte", "lt")
          .containsExactly(
              "eventTime", "2021-01-01T00:00:00.000+0000", "2023-01-01T00:00:00.000+0000");
    };
  }

  private static Consumer<SearchTermsQuery> assertTenantCheck(final List<String> tenantIds) {
    return termsQuery -> {
      assertThat(termsQuery.field()).isEqualTo("tenantId");
      assertThat(termsQuery.values())
          .extracting("value")
          .containsExactlyInAnyOrderElementsOf(tenantIds);
    };
  }

  @Test
  public void shouldQueryByStartTimeAndEndTime() {
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var filter = FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant).isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
  }

  @Test
  public void shouldQueryByStartTimeEndTimeAndTenantId() {
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var filter =
        FilterBuilders.usageMetrics(
            f -> f.startTime(startTime).endTime(endTime).tenantId("tenant1"));

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            searchBoolQuery -> {
              assertThat(searchBoolQuery.must()).hasSize(2);
              assertThat(searchBoolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
              assertThat(searchBoolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      searchTermQuery -> {
                        assertThat(searchTermQuery)
                            .extracting("field", "value.value")
                            .containsExactly(UsageMetricIndex.TENANT_ID, "tenant1");
                      });
            });
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenEnabled() {
    // given
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var authorization =
        Authorization.of(
            a -> a.processDefinition().readProcessInstance().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime)),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
  }

  @Test
  public void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessInstance());
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime)),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  public void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime)),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
  }

  @Test
  public void shouldApplyTenantCheck() {
    // given
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime)),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            searchBoolQuery -> {
              assertThat(searchBoolQuery.must()).hasSize(2);
              assertThat(searchBoolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
              assertThat(searchBoolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermsQuery.class, assertTenantCheck(List.of("a", "b")));
            });
  }

  @Test
  public void shouldIgnoreTenantCheckWhenDisabled() {
    // given
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var tenantCheck = TenantCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime)),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant).isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
  }

  @Test
  public void shouldApplyFilterAndChecks() {
    // given
    final var startTime = OffsetDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var tenantCheck = TenantCheck.enabled(List.of("a", "b"));
    final var resourceAccessChecks = ResourceAccessChecks.of(authorizationCheck, tenantCheck);

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.usageMetrics(f -> f.startTime(startTime).endTime(endTime)),
            resourceAccessChecks);

    // then
    final var queryVariant = searchQuery.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            searchBoolQuery -> {
              assertThat(searchBoolQuery.must()).hasSize(2);
              assertThat(searchBoolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(SearchRangeQuery.class, assertRangeQuery());
              assertThat(searchBoolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermsQuery.class, assertTenantCheck(List.of("a", "b")));
            });
  }
}

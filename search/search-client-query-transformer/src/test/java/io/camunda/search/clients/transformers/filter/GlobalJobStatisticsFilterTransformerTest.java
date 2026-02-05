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
import io.camunda.security.auth.Authorization.Builder;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlobalJobStatisticsFilterTransformerTest extends AbstractTransformerTest {

  @Test
  void shouldReturnNullWhenNoFilters() {
    // given
    final var filter = FilterBuilders.globalJobStatistics(f -> f);

    // when
    final var searchQuery = transformQuery(filter);

    // then - no filter query needed when there are no filters
    assertThat(searchQuery).isNull();
  }

  @Test
  void shouldQueryByFromTime() {
    // given
    final var fromTime = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    final var filter = FilterBuilders.globalJobStatistics(f -> f.from(fromTime));

    // when
    final var searchQuery = transformQuery(filter);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            rangeQuery -> {
              assertThat(rangeQuery.field()).isEqualTo("startTime");
              assertThat(rangeQuery.gte()).isNotNull();
            });
  }

  @Test
  void shouldQueryByToTime() {
    // given
    final var toTime = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    final var filter = FilterBuilders.globalJobStatistics(f -> f.to(toTime));

    // when
    final var searchQuery = transformQuery(filter);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            rangeQuery -> {
              assertThat(rangeQuery.field()).isEqualTo("endTime");
              assertThat(rangeQuery.lte()).isNotNull();
            });
  }

  @Test
  void shouldQueryByFromAndToTime() {
    // given
    final var fromTime = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    final var toTime = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    final var filter = FilterBuilders.globalJobStatistics(f -> f.from(fromTime).to(toTime));

    // when
    final var searchQuery = transformQuery(filter);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              assertThat(boolQuery.must()).hasSize(2);

              // First clause should be range on startTime (from)
              assertThat(boolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchRangeQuery.class,
                      rangeQuery -> {
                        assertThat(rangeQuery.field()).isEqualTo("startTime");
                        assertThat(rangeQuery.gte()).isNotNull();
                      });

              // Second clause should be range on endTime (to)
              assertThat(boolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchRangeQuery.class,
                      rangeQuery -> {
                        assertThat(rangeQuery.field()).isEqualTo("endTime");
                        assertThat(rangeQuery.lte()).isNotNull();
                      });
            });
  }

  @Test
  void shouldQueryByJobType() {
    // given
    final var filter = FilterBuilders.globalJobStatistics(f -> f.jobType("myJobType"));

    // when
    final var searchQuery = transformQuery(filter);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            termQuery -> {
              assertThat(termQuery.field()).isEqualTo("jobType");
              assertThat(termQuery.value().stringValue()).isEqualTo("myJobType");
            });
  }

  @Test
  void shouldQueryByAllFilters() {
    // given
    final var fromTime = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    final var toTime = OffsetDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    final var filter =
        FilterBuilders.globalJobStatistics(
            f -> f.from(fromTime).to(toTime).jobType("workerJobType"));

    // when
    final var searchQuery = transformQuery(filter);

    // then
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchBoolQuery.class,
            boolQuery -> {
              assertThat(boolQuery.must()).hasSize(3);

              // First must clause should be range on startTime (from)
              assertThat(boolQuery.must().get(0).queryOption())
                  .isInstanceOfSatisfying(
                      SearchRangeQuery.class,
                      rangeQuery -> assertThat(rangeQuery.field()).isEqualTo("startTime"));

              // Second must clause should be range on endTime (to)
              assertThat(boolQuery.must().get(1).queryOption())
                  .isInstanceOfSatisfying(
                      SearchRangeQuery.class,
                      rangeQuery -> assertThat(rangeQuery.field()).isEqualTo("endTime"));

              // Third must clause should be term on jobType
              assertThat(boolQuery.must().get(2).queryOption())
                  .isInstanceOfSatisfying(
                      SearchTermQuery.class,
                      termQuery -> {
                        assertThat(termQuery.field()).isEqualTo("jobType");
                        assertThat(termQuery.value().stringValue()).isEqualTo("workerJobType");
                      });
            });
  }

  @Test
  void shouldIgnoreAuthorizationCheckWhenEnabled() {
    // given
    final var fromTime = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    final var authorization =
        Authorization.of(a -> a.readJobMetric().resourceIds(List.of("1", "2")));
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.globalJobStatistics(f -> f.from(fromTime)), resourceAccessChecks);

    // then - authorization is ignored for job metrics, only filter query is returned
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            rangeQuery -> assertThat(rangeQuery.field()).isEqualTo("startTime"));
  }

  @Test
  void shouldReturnNonMatchWhenNoResourceIdsProvided() {
    // given
    final var fromTime = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    final var authorization = Authorization.of(Builder::readJobMetric);
    final var authorizationCheck = AuthorizationCheck.enabled(authorization);
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.globalJobStatistics(f -> f.from(fromTime)), resourceAccessChecks);

    // then - when authorization is enabled but no resource IDs are granted, return matchNone
    assertThat(searchQuery.queryOption()).isInstanceOf(SearchMatchNoneQuery.class);
  }

  @Test
  void shouldIgnoreAuthorizationCheckWhenDisabled() {
    // given
    final var fromTime = OffsetDateTime.of(2024, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);
    final var authorizationCheck = AuthorizationCheck.disabled();
    final var resourceAccessChecks =
        ResourceAccessChecks.of(authorizationCheck, TenantCheck.disabled());

    // when
    final var searchQuery =
        transformQuery(
            FilterBuilders.globalJobStatistics(f -> f.from(fromTime)), resourceAccessChecks);

    // then - authorization is disabled, only filter query is returned
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchRangeQuery.class,
            rangeQuery -> assertThat(rangeQuery.field()).isEqualTo("startTime"));
  }

  @Test
  void shouldApplyTenantCheck() {
    // given
    final var tenantCheck = TenantCheck.enabled(List.of("tenant1", "tenant2"));
    final var resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), tenantCheck);

    // when
    final var searchQuery =
        transformQuery(FilterBuilders.globalJobStatistics(f -> f), resourceAccessChecks);

    // then - tenant check should be applied
    assertThat(searchQuery.queryOption())
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            termsQuery -> {
              assertThat(termsQuery.field()).isEqualTo("tenantId");
              assertThat(termsQuery.values()).hasSize(2);
            });
  }
}

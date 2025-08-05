/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(UsageMetricsController.class)
public class UsageMetricsControllerTest extends RestControllerTest {
  static final String USAGE_METRICS_URL = "/v2/system/usage-metrics";

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
             "processInstances": 5,
             "decisionInstances": 23,
             "activeTenants": 2,
             "assignees": 4,
             "tenants": {}
          }""";

  static final String EXPECTED_TENANTS_SEARCH_RESPONSE =
      """
          {
             "processInstances": 5,
             "decisionInstances": 23,
             "activeTenants": 2,
             "assignees": 0,
             "tenants": {
               "tenant1": {
                 "processInstances": 1,
                 "decisionInstances": 2,
                 "assignees": 1
               },
               "tenant2": {
                 "processInstances": 4,
                 "decisionInstances": 21,
                 "assignees": 3
               }
             }
          }""";

  @MockitoBean UsageMetricsServices usageMetricsServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupUsageMetricsServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(usageMetricsServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(usageMetricsServices);
  }

  @Test
  void shouldSearchWithStartTimeAndEndTime() {
    // given
    when(usageMetricsServices.search(any()))
        .thenReturn(
            SearchQueryResult.of(
                Tuple.of(
                    new UsageMetricStatisticsEntity(5L, 23L, 2L, null),
                    new UsageMetricTUStatisticsEntity(4, null))));
    // when/then
    webClient
        .get()
        .uri(
            USAGE_METRICS_URL
                + "?startTime=1970-11-14T10:50:26.000Z&endTime=2024-12-31T10:50:26.000Z")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    final var startTime = OffsetDateTime.of(1970, 11, 14, 10, 50, 26, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2024, 12, 31, 10, 50, 26, 0, ZoneOffset.UTC);

    verify(usageMetricsServices)
        .search(UsageMetricsQuery.of(q -> q.filter(f -> f.startTime(startTime).endTime(endTime))));
  }

  @Test
  void shouldSearchWithStartTimeAndEndTimeAndTenants() {
    // given
    final var tenants =
        Map.of(
            "tenant1",
            new UsageMetricStatisticsEntityTenant(1L, 2L),
            "tenant2",
            new UsageMetricStatisticsEntityTenant(4L, 21L));
    final var tuTenants =
        Map.of(
            "tenant1",
            new UsageMetricTUStatisticsEntityTenant(1L),
            "tenant2",
            new UsageMetricTUStatisticsEntityTenant(3L));
    when(usageMetricsServices.search(any()))
        .thenReturn(
            SearchQueryResult.of(
                Tuple.of(
                    new UsageMetricStatisticsEntity(5L, 23L, 2L, tenants),
                    new UsageMetricTUStatisticsEntity(0, tuTenants))));
    // when/then
    webClient
        .get()
        .uri(
            USAGE_METRICS_URL
                + "?startTime=1970-11-14T10:50:26.000Z&endTime=2024-12-31T10:50:26.000Z&withTenants=true")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_TENANTS_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    final var startTime = OffsetDateTime.of(1970, 11, 14, 10, 50, 26, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2024, 12, 31, 10, 50, 26, 0, ZoneOffset.UTC);

    verify(usageMetricsServices)
        .search(
            UsageMetricsQuery.of(
                q -> q.filter(f -> f.startTime(startTime).endTime(endTime).withTenants(true))));
  }

  @Test
  void shouldYieldBadRequestIfStartTimeAndEndTimeAreInvalid() {
    // given
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"The provided startTime 'foo' cannot be parsed as a date according to RFC 3339, \
        section 5.6. The provided endTime 'bar' cannot be parsed as a date according to RFC 3339, \
        section 5.6.",
          "instance":"/v2/system/usage-metrics"
        }
        """;
    // when/then
    webClient
        .get()
        .uri(USAGE_METRICS_URL + "?startTime=foo&endTime=bar")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldYieldBadRequestIfNoStartAndEndTimeAreGiven() {
    // given
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"The startTime and endTime must both be specified.",
          "instance":"/v2/system/usage-metrics"
        }
        """;
    // when/then
    webClient
        .get()
        .uri(USAGE_METRICS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldYieldBadRequestIfNoStartTimeIsGiven() {
    // given
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"The startTime and endTime must both be specified.",
          "instance":"/v2/system/usage-metrics"
        }
        """;
    // when/then
    webClient
        .get()
        .uri(USAGE_METRICS_URL + "?endTime=2024-12-31T10:50:26.000Z")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldYieldBadRequestIfNoEndTimeIsGiven() {
    // given
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"The startTime and endTime must both be specified.",
          "instance":"/v2/system/usage-metrics"
        }
        """;
    // when/then
    webClient
        .get()
        .uri(USAGE_METRICS_URL + "?startTime=1970-11-14T10:50:26.000Z")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }
}

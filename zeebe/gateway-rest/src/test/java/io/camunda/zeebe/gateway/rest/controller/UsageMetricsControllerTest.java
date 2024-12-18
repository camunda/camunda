/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UsageMetricsCount;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(UsageMetricsController.class)
public class UsageMetricsControllerTest extends RestControllerTest {
  static final String USAGE_METRICS_URL = "/v2/usage-metrics/";
  static final String USAGE_METRICS_SEARCH_URL = USAGE_METRICS_URL + "search";

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
         "assignees": 5,
         "processInstances": 23,
         "decisionInstances": 17
      }""";

  static final UsageMetricsCount USAGE_METRICS_COUNT_ENTITY = new UsageMetricsCount(5L, 23L, 17L);

  @MockBean UsageMetricsServices usageMetricsServices;

  @BeforeEach
  void setupUsageMetricsServices() {
    when(usageMetricsServices.withAuthentication(any(Authentication.class)))
        .thenReturn(usageMetricsServices);
  }

  @Test
  void shouldSearchWithStartTimeAndEndTime() {
    // given
    when(usageMetricsServices.search(any(UsageMetricsQuery.class)))
        .thenReturn(USAGE_METRICS_COUNT_ENTITY);
    final var request =
        """
        {
           "startTime": "1970-11-14T10:50:26.000Z",
           "endTime":   "2024-12-31T10:50:26.000Z"
        }
        """;
    // when/then
    webClient
        .post()
        .uri(USAGE_METRICS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    final var startTime = OffsetDateTime.of(1970, 11, 14, 10, 50, 26, 0, ZoneOffset.UTC);
    final var endTime = OffsetDateTime.of(2024, 12, 31, 10, 50, 26, 0, ZoneOffset.UTC);

    verify(usageMetricsServices)
        .search(
            new UsageMetricsQuery.Builder()
                .filter(
                    new UsageMetricsFilter.Builder()
                        .startTime(Operation.gte(startTime))
                        .endTime(Operation.lte(endTime))
                        .build())
                .build());
  }

  @Test
  void shouldYieldBadRequestIfNoStartAndEndTimeAreGiven() {
    // given
    final var request = "{}";
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"startTime and endTime must be specified.",
          "instance":"/v2/usage-metrics/search"
        }
        """;
    // when/then
    webClient
        .post()
        .uri(USAGE_METRICS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  void shouldYieldBadRequestIfNoStartTimeIsGiven() {
    // given
    final var request =
        """
        {
           "endTime":   "2024-12-31T10:50:26.000Z"
        }
        """;
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"startTime and endTime must be specified.",
          "instance":"/v2/usage-metrics/search"
        }
        """;
    // when/then
    webClient
        .post()
        .uri(USAGE_METRICS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  void shouldYieldBadRequestIfNoEndTimeIsGiven() {
    // given
    final var request =
        """
        {
           "startTime":   "2024-12-31T10:50:26.000Z"
        }
        """;
    final var expectedResponse =
        """
        {
          "type":"about:blank",
          "title":"INVALID_ARGUMENT",
          "status":400,
          "detail":"startTime and endTime must be specified.",
          "instance":"/v2/usage-metrics/search"
        }
        """;
    // when/then
    webClient
        .post()
        .uri(USAGE_METRICS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(expectedResponse);
  }
}

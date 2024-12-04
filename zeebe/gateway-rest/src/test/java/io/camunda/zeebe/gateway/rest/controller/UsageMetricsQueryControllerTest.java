/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

@WebMvcTest(
    value = UsageMetricsQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class UsageMetricsQueryControllerTest extends RestControllerTest {
  static final String USAGE_METRICS_URL = "/v2/usage-metrics/";
  static final String USAGE_METRICS_SEARCH_URL = USAGE_METRICS_URL + "search";

  static final String USAGE_METRICS_ENTITY_JSON =
      """
      {
         "assignees": 15,
         "processInstances": 12345,
         "decisionInstances": 5354365
      }""";
  static final String EXPECTED_SEARCH_RESPONSE =
      """
       {
         "assignees": 15,
         "processInstances": 12345,
         "decisionInstances": 5354365
       }
     """;

  @Test
  void shouldSearchUsageMetrics() {
    // when

  }
}

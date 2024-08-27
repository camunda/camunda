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

import io.camunda.service.FlownodeInstanceServices;
import io.camunda.service.entities.FlownodeInstanceEntity;
import io.camunda.service.search.query.FlownodeInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = FlownodeInstanceQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class FlownodeInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {}
          ],
          "page": {
              "totalItems": 1,
              "firstSortValues": [],
              "lastSortValues": [
                  "v"
              ]
          }
      }""";

  static final SearchQueryResult<FlownodeInstanceEntity> SEARCH_QUERY_RESULT =
      new Builder<FlownodeInstanceEntity>()
          .total(1L)
          .items(
              List.of(
                  new FlownodeInstanceEntity(
                      1L,
                      2L,
                      3L,
                      "2023-05-17",
                      "2023-05-23",
                      "flowNodeId",
                      "flowNodeName",
                      "processInstanceKey/flowNodeId",
                      "SERVICE_TASK",
                      "COMPLETED",
                      false,
                      null,
                      "<default>")))
          .sortValues(new Object[] {"v"})
          .build();

  static final String FLOWNODE_INSTANCES_SEARCH_URL = "/v2/flownode-instances/search";

  @MockBean FlownodeInstanceServices flownodeInstanceServices;

  @BeforeEach
  void setupServices() {
    when(flownodeInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(flownodeInstanceServices);
  }

  @Test
  void shouldSearchFlownodeInstancesDecisionWithEmptyBody() {
    // given
    when(flownodeInstanceServices.search(any(FlownodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(FLOWNODE_INSTANCES_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(flownodeInstanceServices).search(new FlownodeInstanceQuery.Builder().build());
  }
}

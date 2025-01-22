/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.search.filter.DecisionRequirementsFilter;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.search.sort.DecisionRequirementsSort;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = DecisionRequirementsQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class DecisionRequirementsQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {
                  "tenantId": "t",
                  "decisionRequirementsKey": "0",
                  "decisionRequirementsName": "name",
                  "version": 1,
                  "decisionRequirementsId": "id",
                  "resourceName": "rN"
              }
          ],
          "page": {
              "totalItems": 1,
              "firstSortValues": [],
              "lastSortValues": [
                  "v"
              ]
          }
      }""";

  static final SearchQueryResult<DecisionRequirementsEntity> SEARCH_QUERY_RESULT =
      new Builder<DecisionRequirementsEntity>()
          .total(1L)
          .items(List.of(new DecisionRequirementsEntity("t", 0L, "id", "name", 1, "rN", null)))
          .sortValues(new Object[] {"v"})
          .build();

  static final String DECISION_REQUIREMENTS_SEARCH_URL = "/v2/decision-requirements/search";

  @MockBean DecisionRequirementsServices decisionRequirementsServices;

  @BeforeEach
  void setupServices() {
    when(decisionRequirementsServices.withAuthentication(any(Authentication.class)))
        .thenReturn(decisionRequirementsServices);
  }

  @Test
  void shouldSearchDecisionRequirementsWithEmptyBody() {
    // given
    when(decisionRequirementsServices.search(any(DecisionRequirementsQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(DECISION_REQUIREMENTS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(decisionRequirementsServices)
        .search(
            new DecisionRequirementsQuery.Builder().resultConfig(b -> b.xml().exclude()).build());
  }

  @Test
  void shouldSearchDecisionRequirementsWithEmptyQuery() {
    // given
    when(decisionRequirementsServices.search(any(DecisionRequirementsQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(DECISION_REQUIREMENTS_SEARCH_URL)
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

    verify(decisionRequirementsServices)
        .search(
            new DecisionRequirementsQuery.Builder().resultConfig(b -> b.xml().exclude()).build());
  }

  @Test
  void shouldSearchDecisionRequirementsWithAllFilters() {
    // given
    when(decisionRequirementsServices.search(any(DecisionRequirementsQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
          "filter":{
            "tenantId": "t",
            "decisionRequirementsKey": 0,
            "decisionRequirementsName": "name",
            "version": 1,
            "decisionRequirementsId": "drId"
          }
        }""";

    // when / then
    webClient
        .post()
        .uri(DECISION_REQUIREMENTS_SEARCH_URL)
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

    verify(decisionRequirementsServices)
        .search(
            new DecisionRequirementsQuery.Builder()
                .filter(
                    new DecisionRequirementsFilter.Builder()
                        .tenantIds("t")
                        .decisionRequirementsKeys(0L)
                        .dmnDecisionRequirementsNames("name")
                        .versions(1)
                        .dmnDecisionRequirementsIds("drId")
                        .build())
                .resultConfig(b -> b.xml().exclude())
                .build());
  }

  @Test
  void shouldSearchDecisionRequirementsWithSorting() {
    // given
    when(decisionRequirementsServices.search(any(DecisionRequirementsQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
            "sort": [
                {
                    "field": "version",
                    "order": "asc"
                },
                {
                    "field": "dmnDecisionRequirementsName",
                    "order": "asc"
                },
                {
                    "field": "tenantId",
                    "order": "desc"
                },
                {
                    "field": "decisionRequirementsKey",
                    "order": "asc"
                },
                {
                    "field": "dmnDecisionRequirementsId",
                    "order": "asc"
                }
            ]
        }""";
    // when / then
    webClient
        .post()
        .uri(DECISION_REQUIREMENTS_SEARCH_URL)
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

    verify(decisionRequirementsServices)
        .search(
            new DecisionRequirementsQuery.Builder()
                .sort(
                    new DecisionRequirementsSort.Builder()
                        .version()
                        .asc()
                        .dmnDecisionRequirementsName()
                        .asc()
                        .tenantId()
                        .desc()
                        .decisionRequirementsKey()
                        .asc()
                        .dmnDecisionRequirementsId()
                        .asc()
                        .build())
                .resultConfig(b -> b.xml().exclude())
                .build());
  }

  @Test
  void shouldInvalidateDecisionRequirementsSearchQueryWithBadSortField() {
    // given
    final var request =
        """
        {
            "sort": [
                {
                    "field": "unknownField",
                    "order": "asc"
                }
            ]
        }""";
    final var expectedResponse =
        String.format(
            """
        {
          "type": "about:blank",
          "title": "INVALID_ARGUMENT",
          "status": 400,
          "detail": "Unknown sortBy: unknownField.",
          "instance": "%s"
        }""",
            DECISION_REQUIREMENTS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(DECISION_REQUIREMENTS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(decisionRequirementsServices, never()).search(any(DecisionRequirementsQuery.class));
  }
}

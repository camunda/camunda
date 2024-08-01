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

import io.camunda.service.DecisionRequirementServices;
import io.camunda.service.entities.DecisionRequirementEntity;
import io.camunda.service.search.filter.DecisionRequirementFilter;
import io.camunda.service.search.query.DecisionRequirementQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.search.sort.DecisionRequirementSort;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = DecisionRequirementQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class DecisionRequirementQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {
                  "tenantId": "t",
                  "key": 0,
                  "id": "id",
                  "name": "name",
                  "version": 1,
                  "decisionRequirementsId": "drId",
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

  static final SearchQueryResult<DecisionRequirementEntity> SEARCH_QUERY_RESULT =
      new Builder<DecisionRequirementEntity>()
          .total(1L)
          .items(List.of(new DecisionRequirementEntity("t", 0L, "id", "drId", "name", 1, "rN")))
          .sortValues(new Object[] {"v"})
          .build();

  static final String DECISION_REQUIREMENTS_SEARCH_URL = "/v2/decision-requirements/search";

  @MockBean DecisionRequirementServices decisionRequirementServices;

  @BeforeEach
  void setupServices() {
    when(decisionRequirementServices.withAuthentication(any(Authentication.class)))
        .thenReturn(decisionRequirementServices);
  }

  @Test
  void shouldSearchDecisionRequirementWithEmptyBody() {
    // given
    when(decisionRequirementServices.search(any(DecisionRequirementQuery.class)))
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

    verify(decisionRequirementServices).search(new DecisionRequirementQuery.Builder().build());
  }

  @Test
  void shouldSearchDecisionRequirementWithEmptyQuery() {
    // given
    when(decisionRequirementServices.search(any(DecisionRequirementQuery.class)))
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

    verify(decisionRequirementServices).search(new DecisionRequirementQuery.Builder().build());
  }

  @Test
  void shouldSearchDecisionRequirementWithAllFilters() {
    // given
    when(decisionRequirementServices.search(any(DecisionRequirementQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
          "filter":{
            "tenantId": "t",
            "key": 0,
            "id": "id",
            "name": "name",
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

    verify(decisionRequirementServices)
        .search(
            new DecisionRequirementQuery.Builder()
                .filter(
                    new DecisionRequirementFilter.Builder()
                        .tenantIds("t")
                        .keys(0L)
                        .ids("id")
                        .names("name")
                        .versions(1)
                        .decisionRequirementsIds("drId")
                        .build())
                .build());
  }

  @Test
  void shouldSearchDecisionRequirementWithSorting() {
    // given
    when(decisionRequirementServices.search(any(DecisionRequirementQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
            "sort": [
                {
                    "field": "id",
                    "order": "desc"
                },
                {
                    "field": "version",
                    "order": "asc"
                },
                {
                    "field": "name",
                    "order": "asc"
                },
                {
                    "field": "decisionRequirementsId",
                    "order": "asc"
                },
                {
                    "field": "tenantId",
                    "order": "desc"
                },
                {
                    "field": "key",
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

    verify(decisionRequirementServices)
        .search(
            new DecisionRequirementQuery.Builder()
                .sort(
                    new DecisionRequirementSort.Builder()
                        .id()
                        .desc()
                        .version()
                        .asc()
                        .name()
                        .asc()
                        .decisionRequirementsId()
                        .asc()
                        .tenantId()
                        .desc()
                        .decisionRequirementsKey()
                        .asc()
                        .build())
                .build());
  }

  @Test
  void shouldInvalidateDecisionRequirementSearchQueryWithBadSortField() {
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

    verify(decisionRequirementServices, never()).search(any(DecisionRequirementQuery.class));
  }
}

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.DecisionRequirementsSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.nio.charset.StandardCharsets;
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
                      "decisionRequirementsKey": 0,
                      "name": "name",
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
  static final String DECISION_REQUIREMENTS_GET_XML_URL = "/v2/decision-requirements/%d/xml";

  private static final Long VALID_DECISION_REQUIREMENTS_KEY = 1L;
  private static final Long INVALID_DECISION_REQUIREMENTS_KEY = 999L;

  private static final String DECISION_REQUIREMENTS_ITEM_JSON =
      """
          {
            "tenantId": "t",
            "decisionRequirementsKey": 1,
            "name": "name",
            "version": 1,
            "decisionRequirementsId": "id",
            "resourceName": "rN"
          }
          """;
  @MockBean DecisionRequirementsServices decisionRequirementsServices;

  @BeforeEach
  void setupServices() {
    when(decisionRequirementsServices.withAuthentication(any(Authentication.class)))
        .thenReturn(decisionRequirementsServices);

    when(decisionRequirementsServices.getByKey(VALID_DECISION_REQUIREMENTS_KEY))
        .thenReturn(new DecisionRequirementsEntity("t", 1L, "id", "name", 1, "rN", null));

    when(decisionRequirementsServices.getByKey(INVALID_DECISION_REQUIREMENTS_KEY))
        .thenThrow(
            new NotFoundException(
                "Decision requirements with key "
                    + INVALID_DECISION_REQUIREMENTS_KEY
                    + " not found"));
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

    verify(decisionRequirementsServices)
        .search(
            new DecisionRequirementsQuery.Builder()
                .filter(
                    new DecisionRequirementsFilter.Builder()
                        .tenantIds("t")
                        .decisionRequirementsKeys(0L)
                        .names("name")
                        .versions(1)
                        .decisionRequirementsIds("drId")
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
                        "field": "name",
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
                        "field": "decisionRequirementsId",
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
                        .name()
                        .asc()
                        .tenantId()
                        .desc()
                        .decisionRequirementsKey()
                        .asc()
                        .decisionRequirementsId()
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

  @Test
  public void shouldReturnDecisionRequirementsForValidKey() throws Exception {
    webClient
        .get()
        .uri("/v2/decision-requirements/{decisionRequirementsKey}", VALID_DECISION_REQUIREMENTS_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(DECISION_REQUIREMENTS_ITEM_JSON);

    verify(decisionRequirementsServices, times(1)).getByKey(VALID_DECISION_REQUIREMENTS_KEY);
  }

  @Test
  public void shouldReturn404ForInvalidDecisionRequirementsKey() throws Exception {
    webClient
        .get()
        .uri(
            "/v2/decision-requirements/{decisionRequirementsKey}",
            INVALID_DECISION_REQUIREMENTS_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            """
                {
                  "type": "about:blank",
                  "title": "NOT_FOUND",
                  "status": 404,
                  "detail": "Decision requirements with key 999 not found"
                }
                """);

    verify(decisionRequirementsServices, times(1)).getByKey(INVALID_DECISION_REQUIREMENTS_KEY);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() throws Exception {
    when(decisionRequirementsServices.getByKey(VALID_DECISION_REQUIREMENTS_KEY))
        .thenThrow(new RuntimeException("Unexpected error"));

    webClient
        .get()
        .uri("/v2/decision-requirements/{decisionRequirementsKey}", VALID_DECISION_REQUIREMENTS_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .json(
            """
                {
                  "type": "about:blank",
                  "title": "java.lang.RuntimeException",
                  "status": 500,
                  "detail": "Unexpected error occurred during the request processing: Unexpected error",
                  "instance": "/v2/decision-requirements/1"
                }
                """);

    verify(decisionRequirementsServices, times(1)).getByKey(VALID_DECISION_REQUIREMENTS_KEY);
  }

  @Test
  public void shouldGetRequirementsXml() {
    // given
    final Long decisionRequirementsKey = 1L;
    final String xml = "<xml/>";
    when(decisionRequirementsServices.getDecisionRequirementsXml(decisionRequirementsKey))
        .thenReturn(xml);

    // when/then
    webClient
        .get()
        .uri(DECISION_REQUIREMENTS_GET_XML_URL.formatted(decisionRequirementsKey))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(new MediaType("text", "xml", StandardCharsets.UTF_8))
        .expectBody()
        .xml(xml);
  }

  @Test
  public void shouldReturn404ForNotFoundDecisionRequirementsXml() {
    // given
    final Long decisionRequirementsKey = 1L;
    when(decisionRequirementsServices.getDecisionRequirementsXml(decisionRequirementsKey))
        .thenThrow(new NotFoundException("Decision with key 1 was not found."));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "Decision with key 1 was not found.",
              "instance": "%s"
            }"""
            .formatted(DECISION_REQUIREMENTS_GET_XML_URL.formatted(decisionRequirementsKey));
    webClient
        .get()
        .uri(DECISION_REQUIREMENTS_GET_XML_URL.formatted(decisionRequirementsKey))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  public void shouldReturn500ForInternalError() {
    // given
    final Long decisionRequirementsKey = 1L;
    when(decisionRequirementsServices.getDecisionRequirementsXml(decisionRequirementsKey))
        .thenThrow(new RuntimeException("Failed to get decision requirements xml."));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Failed to get decision requirements xml.",
              "instance": "%s"
            }"""
            .formatted(DECISION_REQUIREMENTS_GET_XML_URL.formatted(decisionRequirementsKey));
    webClient
        .get()
        .uri(DECISION_REQUIREMENTS_GET_XML_URL.formatted(decisionRequirementsKey))
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  public void shouldReturn400ForInvalidKey() {
    // given
    final String decisionRequirementsKey = "invalidKey";

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Failed to convert 'decisionRequirementsKey' with value: 'invalidKey'",
              "instance": "/v2/decision-requirements/invalidKey/xml"
            }""";
    webClient
        .get()
        .uri("/v2/decision-requirements/%s/xml".formatted(decisionRequirementsKey))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);
  }
}
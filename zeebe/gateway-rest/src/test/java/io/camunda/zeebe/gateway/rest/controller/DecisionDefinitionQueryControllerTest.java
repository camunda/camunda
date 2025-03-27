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

import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.DecisionDefinitionSort;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = DecisionDefinitionController.class)
public class DecisionDefinitionQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                      "tenantId": "t",
                      "decisionDefinitionKey": "0",
                      "decisionDefinitionId": "dId",
                      "name": "name",
                      "version": 1,
                      "decisionRequirementsId": "drId",
                      "decisionRequirementsKey": "2"
                  }
              ],
              "page": {
                  "totalItems": 1,
                   "firstSortValues": [
                    { "value": "\\"f\\"", "type": "string" }
                  ],
                  "lastSortValues": [
                    { "value": "\\"v\\"", "type": "string" }
                  ]
              }
          }""";

  static final SearchQueryResult<DecisionDefinitionEntity> SEARCH_QUERY_RESULT =
      new Builder<DecisionDefinitionEntity>()
          .total(1L)
          .items(List.of(new DecisionDefinitionEntity(0L, "dId", "name", 1, "drId", 2L, "t")))
          .firstSortValues(new Object[] {"f"})
          .lastSortValues(new Object[] {"v"})
          .build();

  static final String DECISION_DEFINITIONS_SEARCH_URL = "/v2/decision-definitions/search";
  static final String DECISION_DEFINITIONS_GET_URL = "/v2/decision-definitions/%d";
  static final String DECISION_DEFINITIONS_GET_XML_URL = "/v2/decision-definitions/%d/xml";

  @MockBean DecisionDefinitionServices decisionDefinitionServices;
  @MockBean MultiTenancyConfiguration multiTenancyCfg;

  @BeforeEach
  void setupServices() {
    when(decisionDefinitionServices.withAuthentication(any(Authentication.class)))
        .thenReturn(decisionDefinitionServices);
  }

  @Test
  void shouldSearchDecisionDefinitionsWithEmptyBody() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(decisionDefinitionServices).search(new DecisionDefinitionQuery.Builder().build());
  }

  @Test
  void shouldSearchDecisionDefinitionsWithEmptyQuery() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
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

    verify(decisionDefinitionServices).search(new DecisionDefinitionQuery.Builder().build());
  }

  @Test
  void shouldSearchDecisionDefinitionsWithAllFilters() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
              "filter":{
                "tenantId": "t",
                "decisionDefinitionKey": "0",
                "name": "name",
                "version": 1,
                "decisionRequirementsId": "drId",
                "decisionRequirementsKey": "2",
                "decisionDefinitionId": "dId",
                "decisionRequirementsName": "drName",
                "decisionRequirementsVersion": 3
              }
            }""";

    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
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

    verify(decisionDefinitionServices)
        .search(
            new DecisionDefinitionQuery.Builder()
                .filter(
                    new DecisionDefinitionFilter.Builder()
                        .tenantIds("t")
                        .decisionDefinitionKeys(0L)
                        .names("name")
                        .versions(1)
                        .decisionRequirementsIds("drId")
                        .decisionRequirementsKeys(2L)
                        .decisionDefinitionIds("dId")
                        .build())
                .build());
  }

  @Test
  void shouldSearchDecisionDefinitionsWithFullSorting() {
    // given
    when(decisionDefinitionServices.search(any(DecisionDefinitionQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "decisionDefinitionKey",
                        "order": "ASC"
                    },
                    {
                        "field": "name",
                        "order": "DESC"
                    },
                    {
                        "field": "version",
                        "order": "ASC"
                    },
                    {
                         "field": "decisionDefinitionId"
                    },
                    {
                         "field": "decisionRequirementsKey"
                    },
                    {
                         "field": "decisionRequirementsId"
                    },
                    {
                         "field": "tenantId"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
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

    verify(decisionDefinitionServices)
        .search(
            new DecisionDefinitionQuery.Builder()
                .sort(
                    new DecisionDefinitionSort.Builder()
                        .decisionDefinitionKey()
                        .asc()
                        .name()
                        .desc()
                        .version()
                        .asc()
                        .decisionDefinitionId()
                        .asc()
                        .decisionRequirementsKey()
                        .asc()
                        .decisionRequirementsId()
                        .asc()
                        .tenantId()
                        .asc()
                        .build())
                .build());
  }

  @Test
  void shouldInvalidateDecisionDefinitionsSearchQueryWithBadSortField() {
    // given
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "unknownField",
                        "order": "ASC"
                    }
                ]
            }""";
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [decisionDefinitionKey, decisionDefinitionId, name, version, decisionRequirementsId, decisionRequirementsKey, tenantId]",
              "instance": "%s"
            }"""
            .formatted(DECISION_DEFINITIONS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(DECISION_DEFINITIONS_SEARCH_URL)
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

    verify(decisionDefinitionServices, never()).search(any(DecisionDefinitionQuery.class));
  }

  @Test
  public void shouldGetDecisionDefinitionXml() {
    // given
    final Long decisionDefinitionKey = 1L;
    final String xml = "<xml/>";
    when(decisionDefinitionServices.getDecisionDefinitionXml(decisionDefinitionKey))
        .thenReturn(xml);

    // when/then
    webClient
        .get()
        .uri(DECISION_DEFINITIONS_GET_XML_URL.formatted(decisionDefinitionKey))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(new MediaType("text", "xml", StandardCharsets.UTF_8))
        .expectBody()
        .xml(xml);
  }

  @Test
  public void shouldReturn404ForNotFoundDecisionDefinition() {
    // given
    final Long decisionDefinitionKey = 1L;
    when(decisionDefinitionServices.getDecisionDefinitionXml(decisionDefinitionKey))
        .thenThrow(
            new CamundaSearchException(
                "Decision with key 1 was not found.", CamundaSearchException.Reason.NOT_FOUND));

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
            .formatted(DECISION_DEFINITIONS_GET_XML_URL.formatted(decisionDefinitionKey));
    webClient
        .get()
        .uri(DECISION_DEFINITIONS_GET_XML_URL.formatted(decisionDefinitionKey))
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
    final Long decisionDefinitionKey = 1L;
    when(decisionDefinitionServices.getDecisionDefinitionXml(decisionDefinitionKey))
        .thenThrow(new RuntimeException("Failed to get decision definition xml."));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Failed to get decision definition xml.",
              "instance": "%s"
            }"""
            .formatted(DECISION_DEFINITIONS_GET_XML_URL.formatted(decisionDefinitionKey));
    webClient
        .get()
        .uri(DECISION_DEFINITIONS_GET_XML_URL.formatted(decisionDefinitionKey))
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
    final String decisionDefinitionKey = "invalidKey";

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Failed to convert 'decisionDefinitionKey' with value: 'invalidKey'",
              "instance": "/v2/decision-definitions/invalidKey/xml"
            }""";

    webClient
        .get()
        .uri("/v2/decision-definitions/%s/xml".formatted(decisionDefinitionKey))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  public void shouldGetDecisionDefinitionByKey() {
    // given
    final Long decisionDefinitionKey = 1L;
    final DecisionDefinitionEntity decisionDefinitionEntity =
        new DecisionDefinitionEntity(0L, "dId", "name", 1, "drId", 2L, "t");
    when(decisionDefinitionServices.getByKey(decisionDefinitionKey))
        .thenReturn(decisionDefinitionEntity);
    final var expectedResponse =
        """
            {
              "tenantId": "t",
              "decisionDefinitionKey": "0",
              "decisionDefinitionId": "dId",
              "name": "name",
              "version": 1,
              "decisionRequirementsId": "drId",
              "decisionRequirementsKey": "2"
            }""";
    // when/then
    webClient
        .get()
        .uri("/v2/decision-definitions/%d".formatted(decisionDefinitionKey))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  public void shouldReturn404ForNotFoundDecisionDefinitionByKey() {
    // given
    final Long decisionDefinitionKey = 1L;
    when(decisionDefinitionServices.getByKey(decisionDefinitionKey))
        .thenThrow(
            new CamundaSearchException(
                "Decision with key 1 was not found.", CamundaSearchException.Reason.NOT_FOUND));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "Decision with key 1 was not found.",
              "instance": "/v2/decision-definitions/1"
            }""";
    webClient
        .get()
        .uri("/v2/decision-definitions/%d".formatted(decisionDefinitionKey))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);
  }

  @Test
  public void shouldReturn500ForInternalErrorGetDecisionDefinitionByKey() {
    // given
    final Long decisionDefinitionKey = 1L;
    when(decisionDefinitionServices.getByKey(decisionDefinitionKey))
        .thenThrow(new RuntimeException("Failed to get decision definition."));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Failed to get decision definition.",
              "instance": "/v2/decision-definitions/1"
            }""";
    webClient
        .get()
        .uri("/v2/decision-definitions/%d".formatted(decisionDefinitionKey))
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);
  }

  @ParameterizedTest
  @MethodSource("getDecisionDecisionTestCasesParameters")
  public void shouldReturn403ForForbiddenDecisionDefinition(
      final Pair<String, BiFunction<DecisionDefinitionServices, Long, ?>> testParameters) {
    // given
    final var url = testParameters.getLeft();
    final var service = testParameters.getRight();
    final long decisionDefinitionKey = 1L;
    when(service.apply(decisionDefinitionServices, decisionDefinitionKey))
        .thenThrow(new ForbiddenException(Authorization.of(a -> a.decisionDefinition().read())));
    // when / then
    webClient
        .get()
        .uri(url.formatted(decisionDefinitionKey))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(
            """
                    {
                      "type": "about:blank",
                      "status": 403,
                      "title": "io.camunda.service.exception.ForbiddenException",
                      "detail": "Unauthorized to perform operation 'READ' on resource 'DECISION_DEFINITION'"
                    }
                """);

    // Verify that the service was called with the invalid key
    service.apply(verify(decisionDefinitionServices), decisionDefinitionKey);
  }

  private static Stream<Pair<String, BiFunction<DecisionDefinitionServices, Long, ?>>>
      getDecisionDecisionTestCasesParameters() {
    return Stream.of(
        Pair.of(DECISION_DEFINITIONS_GET_URL, DecisionDefinitionServices::getByKey),
        Pair.of(
            DECISION_DEFINITIONS_GET_XML_URL,
            DecisionDefinitionServices::getDecisionDefinitionXml));
  }
}

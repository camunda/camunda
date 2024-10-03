/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = DecisionInstanceQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class DecisionInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
               "items": [
                   {
                       "decisionInstanceKey": 123,
                       "state": "EVALUATED",
                       "evaluationDate": "2024-06-05T08:29:15.027+0000",
                       "processDefinitionKey": 2251799813688736,
                       "processInstanceKey": 6755399441058457,
                       "decisionDefinitionKey": 123456,
                       "decisionDefinitionId": "di",
                       "decisionDefinitionName": "ddn",
                       "decisionDefinitionVersion": 0,
                       "decisionDefinitionType": "DECISION_TABLE",
                       "result": "result"
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

  static final SearchQueryResult<DecisionInstanceEntity> SEARCH_QUERY_RESULT =
      new SearchQueryResult.Builder<DecisionInstanceEntity>()
          .total(1L)
          .items(
              List.of(
                  new DecisionInstanceEntity(
                      123L,
                      DecisionInstanceState.EVALUATED,
                      "2024-06-05T08:29:15.027+0000",
                      null,
                      2251799813688736L,
                      6755399441058457L,
                      "bpi",
                      "di",
                      "123456",
                      "ddn",
                      0,
                      DecisionDefinitionType.DECISION_TABLE,
                      "result",
                      null,
                      null)))
          .sortValues(new Object[] {"v"})
          .build();

  @MockBean private DecisionInstanceServices decisionInstanceServices;

  @BeforeEach
  void setupServices() {
    when(decisionInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(decisionInstanceServices);
  }

  private static Stream<Arguments> provideQueryParameters() {
    return Stream.of(
        new TestArguments("{}", q -> q),
        new TestArguments(
            """
      {
          "filter": {
              "decisionDefinitionKey": 123456
          }
      }""",
            q -> q.filter(f -> f.decisionDefinitionKeys(123456L))),
        new TestArguments(
            """
      {
          "filter": {
              "decisionDefinitionType": "DECISION_TABLE"
          }
      }""",
            q -> q.filter(f -> f.decisionTypes(DecisionDefinitionType.DECISION_TABLE))),
        new TestArguments(
            """
      {
          "sort": [
                {
                    "field": "decisionDefinitionName",
                    "order": "desc"
                }
          ]
      }""",
            q -> q.sort(s -> s.decisionDefinitionName().desc())));
  }

  @ParameterizedTest
  @MethodSource("provideQueryParameters")
  void shouldReturnDecisionInstances(
      final String apiQuery,
      final Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>>
          expectedQuery) {
    // given
    when(decisionInstanceServices.search(
            SearchQueryBuilders.decisionInstanceSearchQuery(expectedQuery)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when
    webClient
        .post()
        .uri("/v2/decision-instances/search")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(apiQuery)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);
  }

  @Test
  void shouldReturnDecisionInstancesForNullBody() {
    // given
    when(decisionInstanceServices.search(SearchQueryBuilders.decisionInstanceSearchQuery(q -> q)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when
    webClient
        .post()
        .uri("/v2/decision-instances/search")
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);
  }

  @Test
  void shouldReturnDecisionInstanceByKey() {
    // given
    final var decisionInstanceKey = 123L;
    final var decisionInstanceInDB =
        new DecisionInstanceEntity(
            123L,
            DecisionInstanceState.EVALUATED,
            "2024-06-05T08:29:15.027+0000",
            null,
            2251799813688736L,
            6755399441058457L,
            "bpi",
            "di",
            "123456",
            "ddn",
            0,
            DecisionDefinitionType.DECISION_TABLE,
            "result",
            List.of(new DecisionInstanceInputEntity("1", "name", "value")),
            List.of(
                new DecisionInstanceOutputEntity("1", "name1", "value1", "ruleId1", 1),
                new DecisionInstanceOutputEntity("2", "name2", "value2", "ruleId1", 1),
                new DecisionInstanceOutputEntity("3", "name3", "value3", "ruleId2", 2)));
    when(decisionInstanceServices.getByKey(decisionInstanceKey)).thenReturn(decisionInstanceInDB);
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceKey}", decisionInstanceKey)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                {
                     "decisionInstanceKey": 123,
                     "state": "EVALUATED",
                     "evaluationDate": "2024-06-05T08:29:15.027+0000",
                     "processDefinitionKey": 2251799813688736,
                     "processInstanceKey": 6755399441058457,
                     "decisionDefinitionKey": 123456,
                     "decisionDefinitionId": "di",
                     "decisionDefinitionName": "ddn",
                     "decisionDefinitionVersion": 0,
                     "decisionDefinitionType": "DECISION_TABLE",
                     "result": "result",
                     "evaluatedInputs": [
                         {
                             "inputId": "1",
                             "inputName": "name",
                             "inputValue": "value"
                         }
                     ],
                     "matchedRules": [
                         {
                             "ruleId": "ruleId1",
                             "ruleIndex": 1,
                             "evaluatedOutputs": [
                                 {
                                     "outputId": "1",
                                     "outputName": "name1",
                                     "outputValue": "value1"
                                 },
                                 {
                                     "outputId": "2",
                                     "outputName": "name2",
                                     "outputValue": "value2"
                                 }
                             ]
                         },
                         {
                             "ruleId": "ruleId2",
                             "ruleIndex": 2,
                             "evaluatedOutputs": [
                                 {
                                     "outputId": "3",
                                     "outputName": "name3",
                                     "outputValue": "value3"
                                 }
                             ]
                         }
                     ]
                 }""");
  }

  @Test
  void shouldReturn404WhenDecisionInstanceNotFound() {
    // given
    final var decisionInstanceKey = 123L;
    when(decisionInstanceServices.getByKey(decisionInstanceKey))
        .thenThrow(new NotFoundException("Decision Instance with key 1 was not found."));
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceKey}", decisionInstanceKey)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
                {
                          "type": "about:blank",
                          "title": "NOT_FOUND",
                          "status": 404,
                          "detail": "Decision Instance with key 1 was not found.",
                          "instance": "/v2/decision-instances/123"
                        }""");
  }

  @Test
  void shouldReturn500ForInternalErrorGetDecisionDefinitionByKey() {
    // given
    final var decisionInstanceKey = 123L;
    when(decisionInstanceServices.getByKey(decisionInstanceKey))
        .thenThrow(new RuntimeException("Something bad happened."));
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceKey}", decisionInstanceKey)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
                  {
                  "type": "about:blank",
                  "title": "java.lang.RuntimeException",
                  "status": 500,
                  "detail": "Unexpected error occurred during the request processing: Something bad happened.",
                  "instance": "/v2/decision-instances/123"
                }""");
  }

  private record TestArguments(
      String apiQuery,
      Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>> expectedQuery)
      implements Arguments {

    @Override
    public Object[] get() {
      return new Object[] {apiQuery, expectedQuery};
    }
  }
}

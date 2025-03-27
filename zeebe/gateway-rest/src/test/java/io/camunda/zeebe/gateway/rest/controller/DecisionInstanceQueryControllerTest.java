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

import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
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

@WebMvcTest(value = DecisionInstanceController.class)
public class DecisionInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
               "items": [
                   {
                       "decisionInstanceKey": "123",
                       "decisionInstanceId": "123-1",
                       "state": "EVALUATED",
                       "evaluationDate": "2024-06-05T08:29:15.027Z",
                       "processDefinitionKey": "2251799813688736",
                       "processInstanceKey": "6755399441058457",
                       "decisionDefinitionKey": "123456",
                       "decisionDefinitionId": "ddi",
                       "decisionDefinitionName": "ddn",
                       "decisionDefinitionVersion": 0,
                       "decisionDefinitionType": "DECISION_TABLE",
                       "result": "result"
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

  static final SearchQueryResult<DecisionInstanceEntity> SEARCH_QUERY_RESULT =
      new SearchQueryResult.Builder<DecisionInstanceEntity>()
          .total(1L)
          .items(
              List.of(
                  new DecisionInstanceEntity(
                      "123-1",
                      123L,
                      DecisionInstanceState.EVALUATED,
                      OffsetDateTime.parse("2024-06-05T08:29:15.027+00:00"),
                      null,
                      2251799813688736L,
                      6755399441058457L,
                      "tenantId",
                      "ddi",
                      123456L,
                      "ddn",
                      0,
                      DecisionDefinitionType.DECISION_TABLE,
                      "result",
                      null,
                      null)))
          .firstSortValues(new Object[] {"f"})
          .lastSortValues(new Object[] {"v"})
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
          "filter": {
                "evaluationDate": "2024-06-05T08:29:15.027+00:00"
          }
      }""",
            q ->
                q.filter(
                    f ->
                        f.evaluationDateOperations(
                            Operation.eq(OffsetDateTime.parse("2024-06-05T08:29:15.027+00:00"))))),
        new TestArguments(
            """
      {
          "sort": [
                {
                    "field": "decisionDefinitionName",
                    "order": "DESC"
                }
          ]
      }""",
            q -> q.sort(s -> s.decisionDefinitionName().desc())),
        new TestArguments(
            """
      {
          "sort": [
                {
                    "field": "decisionDefinitionName"
                }
          ]
      }""",
            q -> q.sort(s -> s.decisionDefinitionName().asc())));
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
  void shouldReturnDecisionInstanceById() {
    // given
    final var decisionInstanceId = "123-1";
    final var decisionInstanceInDB =
        new DecisionInstanceEntity(
            "123-1",
            123L,
            DecisionInstanceState.EVALUATED,
            OffsetDateTime.parse("2024-06-05T08:29:15.027+00:00"),
            null,
            2251799813688736L,
            6755399441058457L,
            "tenantId",
            "ddi",
            123456L,
            "ddn",
            0,
            DecisionDefinitionType.DECISION_TABLE,
            "result",
            List.of(new DecisionInstanceInputEntity("1", "name", "value")),
            List.of(
                new DecisionInstanceOutputEntity("1", "name1", "value1", "ruleId1", 1),
                new DecisionInstanceOutputEntity("2", "name2", "value2", "ruleId1", 1),
                new DecisionInstanceOutputEntity("3", "name3", "value3", "ruleId2", 2)));
    when(decisionInstanceServices.getById(decisionInstanceId)).thenReturn(decisionInstanceInDB);
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceId}", decisionInstanceId)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
                {
                     "decisionInstanceKey": "123",
                     "state": "EVALUATED",
                     "evaluationDate": "2024-06-05T08:29:15.027Z",
                     "processDefinitionKey": "2251799813688736",
                     "processInstanceKey": "6755399441058457",
                     "decisionDefinitionKey": "123456",
                     "decisionDefinitionId": "ddi",
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
    final var decisionInstanceId = "123-1";
    when(decisionInstanceServices.getById(decisionInstanceId))
        .thenThrow(
            new CamundaSearchException(
                "Decision instance with key 123-1 was not found.",
                CamundaSearchException.Reason.NOT_FOUND));
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceId}", decisionInstanceId)
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
                          "detail": "Decision instance with key 123-1 was not found.",
                          "instance": "/v2/decision-instances/123-1"
                        }""");
  }

  @Test
  void shouldReturn500ForInternalErrorGetDecisionDefinitionByKey() {
    // given
    final var decisionInstanceId = "123-1";
    when(decisionInstanceServices.getById(decisionInstanceId))
        .thenThrow(new RuntimeException("Something bad happened."));
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceId}", decisionInstanceId)
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
                  "instance": "/v2/decision-instances/123-1"
                }""");
  }

  @Test
  void shouldReturn403ForUnauthorizedGetDecisionDefinitionByKey() {
    // given
    final var decisionInstanceId = "123-1";
    when(decisionInstanceServices.getById(decisionInstanceId))
        .thenThrow(
            new ForbiddenException(
                Authorization.of(a -> a.decisionDefinition().readDecisionInstance())));
    // when
    webClient
        .get()
        .uri("/v2/decision-instances/{decisionInstanceId}", decisionInstanceId)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
                  {
                  "type": "about:blank",
                  "title": "io.camunda.service.exception.ForbiddenException",
                  "status": 403,
                  "detail": "Unauthorized to perform operation 'READ_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'",
                  "instance": "/v2/decision-instances/123-1"
                }""");
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    keyOperationTestCases(
        streamBuilder,
        "decisionDefinitionKey",
        ops -> new DecisionInstanceFilter.Builder().decisionDefinitionKeyOperations(ops).build());
    dateTimeOperationTestCases(
        streamBuilder,
        "evaluationDate",
        ops -> new DecisionInstanceFilter.Builder().evaluationDateOperations(ops).build());

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchVariablesWithAdvancedFilter(
      final String filterString, final DecisionInstanceFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    System.out.println("request = " + request);
    when(decisionInstanceServices.search(any(DecisionInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var decisionInstanceKey = 123L;

    // when / then
    webClient
        .post()
        .uri("/v2/decision-instances/search")
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

    verify(decisionInstanceServices)
        .search(new DecisionInstanceQuery.Builder().filter(filter).build());
  }

  @Test
  void shouldThrowExceptionWithInvalidDateTime() {
    // given
    final var request = "{\"filter\": {\"evaluationDate\": \"invalid\"}}";

    // when / then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Failed to parse date-time: [invalid]",
              "instance": "/v2/decision-instances/search"
            }""";
    webClient
        .post()
        .uri("/v2/decision-instances/search")
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

    verify(decisionInstanceServices, never()).search(any(DecisionInstanceQuery.class));
  }

  @Test
  void shouldThrowExceptionWithWrongType() {
    // given
    final var request = "{\"filter\": {\"evaluationDate\": []}";

    // when / then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Request property [filter.evaluationDate] cannot be parsed",
              "instance": "/v2/decision-instances/search"
            }""";
    webClient
        .post()
        .uri("/v2/decision-instances/search")
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

    verify(decisionInstanceServices, never()).search(any(DecisionInstanceQuery.class));
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

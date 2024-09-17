/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.service.search.filter.FilterBuilders.dateValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.service.DecisionInstanceServices;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.service.entities.DecisionInstanceEntity.DecisionInstanceType;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                       "key": 123,
                       "state": "EVALUATED",
                       "evaluationDate": "2024-06-05T08:29:15.027+0000",
                       "processDefinitionKey": 2251799813688736,
                       "processInstanceKey": 6755399441058457,
                       "decisionKey": 123456,
                       "dmnDecisionId": "di",
                       "dmnDecisionName": "ddn",
                       "decisionVersion": 0,
                       "decisionType": "DECISION_TABLE",
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
                      DecisionInstanceType.DECISION_TABLE,
                      "result")))
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
              "evaluationDateAfter": "2024-01-02T03:04:05+00:00",
              "evaluationDateBefore": "2024-02-03T04:05:06+00:00"
          }
      }""",
            q ->
                q.filter(
                        f ->
                            f.evaluationDate(
                                dateValue(
                                    d ->
                                        d.after(
                                                OffsetDateTime.of(
                                                    LocalDateTime.of(2024, 1, 2, 3, 4, 5),
                                                    ZoneOffset.UTC))
                                            .before(
                                                OffsetDateTime.of(
                                                    LocalDateTime.of(2024, 2, 3, 4, 5, 6),
                                                    ZoneOffset.UTC)))))
                    .resultConfig(r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude())),
        new TestArguments(
            """
      {
          "sort": [
                {
                    "field": "dmnDecisionName",
                    "order": "desc"
                }
          ]
      }""",
            q ->
                q.sort(s -> s.dmnDecisionName().desc())
                    .resultConfig(
                        r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude())));
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

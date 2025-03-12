/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AdHocSubprocessActivityEntity;
import io.camunda.search.entities.AdHocSubprocessActivityEntity.ActivityType;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AdHocSubprocessActivityServices;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivityResult;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivityResult.TypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivitySearchQueryResult;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(value = AdHocSubprocessActivityController.class)
class AdHocSubprocessActivityControllerTest extends RestControllerTest {
  private static final String AD_HOC_ACTIVITIES_URL = "/v2/element-instances/ad-hoc-activities";
  private static final String SEARCH_ACTIVITIES_URL = AD_HOC_ACTIVITIES_URL + "/search";

  @MockitoBean private AdHocSubprocessActivityServices adHocSubprocessActivityServices;

  @BeforeEach
  void setUpServices() {
    when(adHocSubprocessActivityServices.withAuthentication(any(Authentication.class)))
        .thenReturn(adHocSubprocessActivityServices);
  }

  @Nested
  class SearchActivities {
    private static final Long PROCESS_DEFINITION_KEY = 2251799813685281L;
    private static final String PROCESS_DEFINITION_ID = "TestParentAdHocSubprocess";
    private static final String AD_HOC_SUBPROCESS_ID = "TestAdHocSubprocess";

    @Test
    void shouldMapSearchResultToSuccessfulResponse() {
      when(adHocSubprocessActivityServices.search(any()))
          .thenReturn(
              new SearchQueryResult.Builder<AdHocSubprocessActivityEntity>()
                  .items(
                      List.of(
                          new AdHocSubprocessActivityEntity(
                              PROCESS_DEFINITION_KEY,
                              PROCESS_DEFINITION_ID,
                              AD_HOC_SUBPROCESS_ID,
                              "task1",
                              "Task #1",
                              ActivityType.SERVICE_TASK,
                              "The first task in the ad-hoc subprocess",
                              null),
                          new AdHocSubprocessActivityEntity(
                              PROCESS_DEFINITION_KEY,
                              PROCESS_DEFINITION_ID,
                              AD_HOC_SUBPROCESS_ID,
                              "task2",
                              "Task #2",
                              ActivityType.USER_TASK,
                              "The second task in the ad-hoc subprocess",
                              null)))
                  .build());

      webClient
          .post()
          .uri(SEARCH_ACTIVITIES_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
            {
              "filter": {
                "processDefinitionKey": 2251799813685281,
                "adHocSubprocessId": "TestAdHocSubprocess"
              }
            }
            """)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(AdHocSubprocessActivitySearchQueryResult.class)
          .consumeWith(
              result -> {
                final var responseBody = result.getResponseBody();
                assertThat(responseBody).isNotNull();
                assertThat(responseBody.getItems())
                    .hasSize(2)
                    .extracting(
                        AdHocSubprocessActivityResult::getProcessDefinitionKey,
                        AdHocSubprocessActivityResult::getProcessDefinitionId,
                        AdHocSubprocessActivityResult::getAdHocSubprocessId,
                        AdHocSubprocessActivityResult::getElementId,
                        AdHocSubprocessActivityResult::getElementName,
                        AdHocSubprocessActivityResult::getType,
                        AdHocSubprocessActivityResult::getDocumentation,
                        AdHocSubprocessActivityResult::getTenantId)
                    .containsExactly(
                        tuple(
                            PROCESS_DEFINITION_KEY.toString(),
                            PROCESS_DEFINITION_ID,
                            AD_HOC_SUBPROCESS_ID,
                            "task1",
                            "Task #1",
                            TypeEnum.SERVICE_TASK,
                            "The first task in the ad-hoc subprocess",
                            null),
                        tuple(
                            PROCESS_DEFINITION_KEY.toString(),
                            PROCESS_DEFINITION_ID,
                            AD_HOC_SUBPROCESS_ID,
                            "task2",
                            "Task #2",
                            TypeEnum.USER_TASK,
                            "The second task in the ad-hoc subprocess",
                            null));
              });

      verify(adHocSubprocessActivityServices)
          .search(
              assertArg(
                  r -> {
                    assertThat(r.filter().processDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
                    assertThat(r.filter().adHocSubprocessId()).isEqualTo(AD_HOC_SUBPROCESS_ID);
                  }));
    }

    @ParameterizedTest
    @MethodSource("invalidSearchParameters")
    void shouldRejectSearchWhenParametersAreInvalidOrMissing(
        final String request, final String expectedErrorDetail) {
      webClient
          .post()
          .uri(SEARCH_ACTIVITIES_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectBody()
          .json(
              """
            {
                "type": "about:blank",
                "title": "INVALID_ARGUMENT",
                "status": 400,
                "instance": "/v2/element-instances/ad-hoc-activities/search"
            }
            """)
          .jsonPath(".detail")
          .isEqualTo(expectedErrorDetail);
    }

    @Test
    void shouldMapServiceExceptionToErrorResponse() {
      when(adHocSubprocessActivityServices.search(any()))
          .thenThrow(
              new CamundaSearchException(
                  "Failed to find Ad-Hoc Subprocess with ID 'TestAdHocSubprocess'",
                  CamundaSearchException.Reason.NOT_FOUND));

      webClient
          .post()
          .uri(SEARCH_ACTIVITIES_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
            {
              "filter": {
                "processDefinitionKey": 2251799813685281,
                "adHocSubprocessId": "TestAdHocSubprocess"
              }
            }
            """)
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
                "detail": "Failed to find Ad-Hoc Subprocess with ID 'TestAdHocSubprocess'",
                "instance": "/v2/element-instances/ad-hoc-activities/search"
            }
            """);
    }

    static Stream<Arguments> invalidSearchParameters() {
      return Stream.of(
          arguments(
              """
              {}
              """,
              "No filter provided."),
          arguments(
              """
              {
                "filter": {}
              }
              """,
              "The value for filter.processDefinitionKey is 'null' but must be a non-negative numeric value. No filter.adHocSubprocessId provided."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 0,
                  "adHocSubprocessId": "TestAdHocSubprocess"
                }
              }
              """,
              "The value for filter.processDefinitionKey is '0' but must be a non-negative numeric value."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": -1,
                  "adHocSubprocessId": "TestAdHocSubprocess"
                }
              }
              """,
              "The value for filter.processDefinitionKey is '-1' but must be a non-negative numeric value."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 2251799813685281
                }
              }
              """,
              "No filter.adHocSubprocessId provided."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 2251799813685281,
                  "adHocSubprocessId": ""
                }
              }
              """,
              "No filter.adHocSubprocessId provided."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 2251799813685281,
                  "adHocSubprocessId": "   "
                }
              }
              """,
              "No filter.adHocSubprocessId provided."));
    }
  }
}

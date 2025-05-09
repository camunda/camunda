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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AdHocSubProcessActivityEntity;
import io.camunda.search.entities.AdHocSubProcessActivityEntity.ActivityType;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivityResult;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivityResult.TypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivitySearchQueryResult;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

@WebMvcTest(value = AdHocSubProcessActivityController.class)
class AdHocSubProcessActivityControllerTest extends RestControllerTest {
  private static final String AD_HOC_ACTIVITIES_URL = "/v2/element-instances/ad-hoc-activities";
  private static final String SEARCH_ACTIVITIES_URL = AD_HOC_ACTIVITIES_URL + "/search";
  private static final String ACTIVATE_ACTIVITIES_URL =
      AD_HOC_ACTIVITIES_URL + "/{adHocSubProcessInstanceKey}/activation";

  @MockitoBean private AdHocSubProcessActivityServices adHocSubProcessActivityServices;

  @BeforeEach
  void setUpServices() {
    when(adHocSubProcessActivityServices.withAuthentication(any(Authentication.class)))
        .thenReturn(adHocSubProcessActivityServices);
  }

  @Nested
  class SearchActivities {
    private static final Long PROCESS_DEFINITION_KEY = 2251799813685281L;
    private static final String PROCESS_DEFINITION_ID = "TestParentAdHocSubProcess";
    private static final String AD_HOC_SUBPROCESS_ID = "TestAdHocSubProcess";

    @Test
    void shouldMapSearchResultToSuccessfulResponse() {
      when(adHocSubProcessActivityServices.search(any()))
          .thenReturn(
              new SearchQueryResult.Builder<AdHocSubProcessActivityEntity>()
                  .items(
                      List.of(
                          new AdHocSubProcessActivityEntity(
                              PROCESS_DEFINITION_KEY,
                              PROCESS_DEFINITION_ID,
                              AD_HOC_SUBPROCESS_ID,
                              "task1",
                              "Task #1",
                              ActivityType.SERVICE_TASK,
                              "The first task in the ad-hoc sub-process",
                              null),
                          new AdHocSubProcessActivityEntity(
                              PROCESS_DEFINITION_KEY,
                              PROCESS_DEFINITION_ID,
                              AD_HOC_SUBPROCESS_ID,
                              "task2",
                              "Task #2",
                              ActivityType.USER_TASK,
                              "The second task in the ad-hoc sub-process",
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
                "adHocSubProcessId": "TestAdHocSubProcess"
              }
            }
            """)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody(AdHocSubProcessActivitySearchQueryResult.class)
          .consumeWith(
              result -> {
                final var responseBody = result.getResponseBody();
                assertThat(responseBody).isNotNull();
                assertThat(responseBody.getItems())
                    .hasSize(2)
                    .extracting(
                        AdHocSubProcessActivityResult::getProcessDefinitionKey,
                        AdHocSubProcessActivityResult::getProcessDefinitionId,
                        AdHocSubProcessActivityResult::getAdHocSubProcessId,
                        AdHocSubProcessActivityResult::getElementId,
                        AdHocSubProcessActivityResult::getElementName,
                        AdHocSubProcessActivityResult::getType,
                        AdHocSubProcessActivityResult::getDocumentation,
                        AdHocSubProcessActivityResult::getTenantId)
                    .containsExactly(
                        tuple(
                            PROCESS_DEFINITION_KEY.toString(),
                            PROCESS_DEFINITION_ID,
                            AD_HOC_SUBPROCESS_ID,
                            "task1",
                            "Task #1",
                            TypeEnum.SERVICE_TASK,
                            "The first task in the ad-hoc sub-process",
                            null),
                        tuple(
                            PROCESS_DEFINITION_KEY.toString(),
                            PROCESS_DEFINITION_ID,
                            AD_HOC_SUBPROCESS_ID,
                            "task2",
                            "Task #2",
                            TypeEnum.USER_TASK,
                            "The second task in the ad-hoc sub-process",
                            null));
              });

      verify(adHocSubProcessActivityServices)
          .search(
              assertArg(
                  r -> {
                    assertThat(r.filter().processDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
                    assertThat(r.filter().adHocSubProcessId()).isEqualTo(AD_HOC_SUBPROCESS_ID);
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

      verifyNoInteractions(adHocSubProcessActivityServices);
    }

    @Test
    void shouldMapServiceExceptionToErrorResponse() {
      when(adHocSubProcessActivityServices.search(any()))
          .thenThrow(
              new CamundaSearchException(
                  "Failed to find ad-hoc sub-process with ID 'TestAdHocSubProcess'",
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
                "adHocSubProcessId": "TestAdHocSubProcess"
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
                "detail": "Failed to find ad-hoc sub-process with ID 'TestAdHocSubProcess'",
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
              "The value for filter.processDefinitionKey is 'null' but must be a non-negative numeric value. No filter.adHocSubProcessId provided."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 0,
                  "adHocSubProcessId": "TestAdHocSubProcess"
                }
              }
              """,
              "The value for filter.processDefinitionKey is '0' but must be a non-negative numeric value."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": -1,
                  "adHocSubProcessId": "TestAdHocSubProcess"
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
              "No filter.adHocSubProcessId provided."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 2251799813685281,
                  "adHocSubProcessId": ""
                }
              }
              """,
              "No filter.adHocSubProcessId provided."),
          arguments(
              """
              {
                "filter": {
                  "processDefinitionKey": 2251799813685281,
                  "adHocSubProcessId": "   "
                }
              }
              """,
              "No filter.adHocSubProcessId provided."));
    }
  }

  @Nested
  class ActivateActivities {

    private static final String AD_HOC_SUBPROCESS_INSTANCE_KEY = "123456789";

    @Test
    void shouldActivateActivities() {
      when(adHocSubProcessActivityServices.activateActivities(
              any(AdHocSubProcessActivateActivitiesRequest.class)))
          .thenReturn(
              CompletableFuture.completedFuture(new AdHocSubProcessActivityActivationRecord()));

      webClient
          .post()
          .uri(ACTIVATE_ACTIVITIES_URL, AD_HOC_SUBPROCESS_INSTANCE_KEY)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
            {
              "elements": [
                {"elementId": "A"},
                {"elementId": "B"},
                {"elementId": "C"}
              ]
            }
            """)
          .exchange()
          .expectStatus()
          .isNoContent();

      verify(adHocSubProcessActivityServices)
          .activateActivities(
              assertArg(
                  request -> {
                    assertThat(request.adHocSubProcessInstanceKey())
                        .isEqualTo(AD_HOC_SUBPROCESS_INSTANCE_KEY);

                    assertThat(request.elements())
                        .extracting(AdHocSubProcessActivateActivityReference::elementId)
                        .containsExactly("A", "B", "C");
                  }));
    }

    @ParameterizedTest
    @MethodSource("invalidActivateParameters")
    void shouldReturnBadRequestWhenValidationFails(
        final String request, final String expectedErrorDetail) {
      webClient
          .post()
          .uri(ACTIVATE_ACTIVITIES_URL, AD_HOC_SUBPROCESS_INSTANCE_KEY)
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
                "instance": "/v2/element-instances/ad-hoc-activities/%s/activation"
            }
            """
                  .formatted(AD_HOC_SUBPROCESS_INSTANCE_KEY))
          .jsonPath(".detail")
          .isEqualTo(expectedErrorDetail);

      verifyNoInteractions(adHocSubProcessActivityServices);
    }

    static Stream<Arguments> invalidActivateParameters() {
      return Stream.of(
          arguments(
              """
              {}
              """,
              "No elements provided."),
          arguments(
              """
              {
                "elements": []
              }
              """,
              "No elements provided."),
          arguments(
              """
              {
                "elements": [
                  {}
                ]
              }
              """,
              "No elements[0].elementId provided."),
          arguments(
              """
              {
                "elements": [
                  { "elementId": null }
                ]
              }
              """,
              "No elements[0].elementId provided."),
          arguments(
              """
              {
                "elements": [
                  { "elementId": "    " }
                ]
              }
              """,
              "No elements[0].elementId provided."));
    }
  }
}

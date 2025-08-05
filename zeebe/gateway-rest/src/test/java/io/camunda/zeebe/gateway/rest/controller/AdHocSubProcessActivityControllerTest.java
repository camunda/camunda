/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = AdHocSubProcessActivityController.class)
class AdHocSubProcessActivityControllerTest extends RestControllerTest {
  private static final String AD_HOC_ACTIVITIES_URL = "/v2/element-instances/ad-hoc-activities";
  private static final String ACTIVATE_ACTIVITIES_URL =
      AD_HOC_ACTIVITIES_URL + "/{adHocSubProcessInstanceKey}/activation";

  @MockitoBean private AdHocSubProcessActivityServices adHocSubProcessActivityServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUpServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(adHocSubProcessActivityServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(adHocSubProcessActivityServices);
  }

  @Nested
  class ActivateActivities {

    private static final long AD_HOC_SUBPROCESS_INSTANCE_KEY = 123456789L;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldActivateActivities(final boolean cancelRemainingInstances) {
      when(adHocSubProcessActivityServices.activateActivities(
              any(AdHocSubProcessActivateActivitiesRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(new AdHocSubProcessInstructionRecord()));

      webClient
          .post()
          .uri(ACTIVATE_ACTIVITIES_URL, AD_HOC_SUBPROCESS_INSTANCE_KEY)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
              {
                "elements": [
                  {
                     "elementId": "A",
                     "variables": {
                        "key": "value"
                     }
                  },
                  {
                      "elementId": "B",
                      "variables":  {}
                  },
                  {"elementId": "C"}
                ],
                "cancelRemainingInstances": %b
              }
              """
                  .formatted(cancelRemainingInstances))
          .exchange()
          .expectStatus()
          .isNoContent();

      verify(adHocSubProcessActivityServices)
          .activateActivities(
              assertArg(
                  request -> {
                    assertThat(request.adHocSubProcessInstanceKey())
                        .isEqualTo(AD_HOC_SUBPROCESS_INSTANCE_KEY);
                    assertThat(request.cancelRemainingInstances())
                        .isEqualTo(cancelRemainingInstances);

                    assertThat(request.elements())
                        .satisfiesExactly(
                            element -> {
                              assertThat(element.elementId()).isEqualTo("A");
                              assertThat(element.variables())
                                  .containsExactly(entry("key", "value"));
                            },
                            element -> {
                              assertThat(element.elementId()).isEqualTo("B");
                              assertThat(element.variables()).isEmpty();
                            },
                            element -> {
                              assertThat(element.elementId()).isEqualTo("C");
                              assertThat(element.variables()).isEmpty();
                            });
                  }));
    }

    @Test
    void shouldActivateActivitiesWithMissingCancelRemainingActivitiesFlag() {
      when(adHocSubProcessActivityServices.activateActivities(
              any(AdHocSubProcessActivateActivitiesRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(new AdHocSubProcessInstructionRecord()));

      webClient
          .post()
          .uri(ACTIVATE_ACTIVITIES_URL, AD_HOC_SUBPROCESS_INSTANCE_KEY)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(
              """
                  {
                    "elements": [
                      {"elementId": "A"}
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
                    assertThat(request.cancelRemainingInstances()).isFalse();

                    assertThat(request.elements())
                        .satisfiesExactly(
                            element -> {
                              assertThat(element.elementId()).isEqualTo("A");
                              assertThat(element.variables()).isEmpty();
                            });
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
                "detail": "%s",
                "instance": "/v2/element-instances/ad-hoc-activities/%s/activation"
            }
            """
                  .formatted(expectedErrorDetail, AD_HOC_SUBPROCESS_INSTANCE_KEY),
              JsonCompareMode.STRICT);

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

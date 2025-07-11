/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AdHocSubProcessActivityServices;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

  @Test
  void shouldActivateAdHocSubProcessActivities() {
    // given
    final String adHocSubProcessInstanceKey = "123456789";
    final AdHocSubProcessActivityActivationRecord activationRecord =
        new AdHocSubProcessActivityActivationRecord();
    when(adHocSubProcessActivityServices.activateActivities(any()))
        .thenReturn(CompletableFuture.completedFuture(activationRecord));

    // when
    webClient
        .post()
        .uri(ACTIVATE_ACTIVITIES_URL, adHocSubProcessInstanceKey)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "elements": [
                {"elementId": "task1"},
                {"elementId": "task2"}
              ]
            }
            """)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(adHocSubProcessActivityServices)
        .activateActivities(
            assertArg(
                (AdHocSubProcessActivateActivitiesRequest request) -> {
                  assertThat(request.adHocSubProcessInstanceKey())
                      .isEqualTo(adHocSubProcessInstanceKey);
                  assertThat(request.elements())
                      .extracting(AdHocSubProcessActivateActivityReference::elementId)
                      .containsExactly("task1", "task2");
                }));
  }

  @Test
  void shouldReturnBadRequestWhenElementsAreEmpty() {
    // given
    final String adHocSubProcessInstanceKey = "123456789";

    // when
    webClient
        .post()
        .uri(ACTIVATE_ACTIVITIES_URL, adHocSubProcessInstanceKey)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "elements": []
            }
            """)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
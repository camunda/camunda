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

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = ProcessInstanceController.class)
public class FaultyControllerTest extends RestControllerTest {

  static final String PROCESS_INSTANCES_START_URL = "/v2/process-instances";

  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ProcessInstanceServices processInstanceServices;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(processInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(processInstanceServices);
  }

  @Test
  void shouldRejectUnrecognizedField() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);

    final var request =
        """
        {
            "processDefinitionKey": "123",
            "test": "123"
        }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_START_URL)
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
                 "title": "Bad Request",
                 "status": 400,
                 "detail": "%s",
                 "instance": "%s"
             }
             """
                .formatted("Request property [test] cannot be parsed", PROCESS_INSTANCES_START_URL),
            JsonCompareMode.STRICT);
  }
}

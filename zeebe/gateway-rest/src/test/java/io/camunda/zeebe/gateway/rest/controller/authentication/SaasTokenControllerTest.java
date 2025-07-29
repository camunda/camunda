/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.authentication;

import static org.mockito.Mockito.when;

import io.camunda.authentication.service.CamundaUserService;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

class SaasTokenControllerTest {
  private static final String BASE_PATH = "/v2/authentication/me";
  private static final String TOKEN_PATH = BASE_PATH + "/token";

  @Nested
  @WebMvcTest(SaaSTokenController.class)
  @TestPropertySource(properties = "spring.profiles.active=consolidated-auth")
  public class SaasNotConfiguredTest extends RestControllerTest {

    @MockitoBean private CamundaUserService camundaUserService;

    @Test
    void shouldReturn404WhenSaaSNotConfigured() {
      // when
      when(camundaUserService.getUserToken()).thenReturn("{b: 'blah'}");
      webClient
          .get()
          .uri(TOKEN_PATH)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isNotFound();
    }
  }

  @Nested
  @WebMvcTest(SaaSTokenController.class)
  @TestPropertySource(
      properties = {
        "spring.profiles.active=consolidated-auth",
        "camunda.security.saas.organizationId=test-org-id",
        "camunda.security.saas.clusterId=test-cluster-id"
      })
  public class SaasConfiguredTest extends RestControllerTest {

    @MockitoBean private CamundaUserService camundaUserService;

    @Test
    void shouldReturn200WhenSaaSConfigured() {
      // when
      when(camundaUserService.getUserToken()).thenReturn("{b: 'blah'}");
      webClient
          .get()
          .uri(TOKEN_PATH)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json("{b: 'blah'}", JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturn401WhenSaaSConfiguredAndTokenNotFound() {
      // when
      when(camundaUserService.getUserToken()).thenReturn(null);
      webClient
          .get()
          .uri(TOKEN_PATH)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isUnauthorized();
    }
  }
}

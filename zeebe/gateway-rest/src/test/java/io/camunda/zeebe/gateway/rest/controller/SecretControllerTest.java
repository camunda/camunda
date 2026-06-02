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

import io.camunda.service.SecretServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(SecretController.class)
public class SecretControllerTest extends RestControllerTest {

  private static final String RESOLVE_ENDPOINT = "/v2/secrets/resolve";

  @MockitoBean SecretServices secretServices;

  @Test
  void shouldReturnResolvedSecrets() {
    when(secretServices.resolve(any())).thenReturn(Map.of("camunda.secrets.FOO", "foo-value"));

    webClient
        .post()
        .uri(RESOLVE_ENDPOINT)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            new SecretController.ResolveSecretsRequest(
                List.of("camunda.secrets.FOO", "camunda.secrets.MISSING")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "resolved": { "camunda.secrets.FOO": "foo-value" } }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnEmptyMapWhenNoneResolved() {
    when(secretServices.resolve(any())).thenReturn(Map.of());

    webClient
        .post()
        .uri(RESOLVE_ENDPOINT)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new SecretController.ResolveSecretsRequest(List.of("camunda.secrets.UNKNOWN")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json("{ \"resolved\": {} }", JsonCompareMode.STRICT);
  }
}

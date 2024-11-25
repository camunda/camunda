/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(MappingController.class)
public class MappingControllerTest extends RestControllerTest {

  private static final String MAPPING_RULES_PATH = "/v2/mapping-rules";

  @MockBean private MappingServices mappingServices;

  @BeforeEach
  void setup() {
    when(mappingServices.withAuthentication(any(Authentication.class))).thenReturn(mappingServices);
  }

  @Test
  void createMappingShouldReturnCreated() {
    // given
    final var dto = validCreateMappingRequest();
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(1L)
            .setClaimName(dto.claimName())
            .setClaimValue(dto.claimValue());

    when(mappingServices.createMapping(dto))
        .thenReturn(CompletableFuture.completedFuture(mappingRecord));

    // when
    webClient
        .post()
        .uri(MAPPING_RULES_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(mappingServices, times(1)).createMapping(dto);
  }

  @Test
  void shouldRejectMappingCreationWithMissingClaimName() {
    // given
    final var request = new MappingRuleCreateRequest().claimValue("claimValue");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimName provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingCreationWitBlankClaimName() {
    // given
    final var request = new MappingRuleCreateRequest().claimName("").claimValue("claimValue");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimName provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingCreationWithMissingClaimValue() {
    // given
    final var request = new MappingRuleCreateRequest().claimName("claimName");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimValue provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingCreationWitBlankClaimValue() {
    // given
    final var request = new MappingRuleCreateRequest().claimName("claimName").claimValue("");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimValue provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  private MappingDTO validCreateMappingRequest() {
    return new MappingDTO("newClaimName", "newClaimValue");
  }

  private void assertRequestRejectedExceptionally(
      final MappingRuleCreateRequest request, final String expectedError) {
    webClient
        .post()
        .uri(MAPPING_RULES_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedError);
  }
}

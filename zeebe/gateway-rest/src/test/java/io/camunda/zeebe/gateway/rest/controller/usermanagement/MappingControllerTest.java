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
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @ParameterizedTest
  @ValueSource(strings = {"foo", "Foo", "foo123", "foo_", "foo.", "foo@"})
  void createMappingShouldReturnCreated(final String id) {
    // given
    final var dto = validCreateMappingRequest();
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(1L)
            .setClaimName(dto.claimName())
            .setClaimValue(dto.claimValue())
            .setMappingRuleId(id)
            .setName(dto.name());

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
  void shouldRejectMappingCreationWithMissingId() {
    // given
    final var request =
        new MappingRuleCreateRequest().claimValue("claimValue").claimName("claim").name("name");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No mappingRuleId provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingCreationWitBlankId() {
    // given
    final var request =
        new MappingRuleCreateRequest()
            .claimName("claim")
            .claimValue("claimValue")
            .name("name")
            .mappingRuleId("");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No mappingRuleId provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingCreationWithMissingClaimName() {
    // given
    final var request =
        new MappingRuleCreateRequest().claimValue("claimValue").name("name").mappingRuleId("id");

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
    final var request =
        new MappingRuleCreateRequest()
            .claimName("")
            .claimValue("claimValue")
            .name("name")
            .mappingRuleId("id");

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
    final var request =
        new MappingRuleCreateRequest().claimName("claimName").name("name").mappingRuleId("id");

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
    final var request =
        new MappingRuleCreateRequest()
            .claimName("claimName")
            .claimValue("")
            .name("name")
            .mappingRuleId("id");

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
  void shouldRejectMappingCreationWithMissingName() {
    // given
    final var request =
        new MappingRuleCreateRequest()
            .claimName("claimName")
            .claimValue("claimValue")
            .mappingRuleId("id");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No name provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "foo~", "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=",
        "foo+", "foo{", "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'",
        "foo<", "foo>", "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
      })
  void shouldRejectMappingCreationWithIllegalCharactersInId(final String id) {
    // given
    final var request =
        new MappingRuleCreateRequest()
            .mappingRuleId(id)
            .claimName("claimName")
            .claimValue("claimValue")
            .name("name");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided mappingRuleId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
            .formatted(IdentifierPatterns.ID_PATTERN, MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingWithTooLongId() {
    // given
    final var id = "x".repeat(257);
    final var request =
        new MappingRuleCreateRequest()
            .mappingRuleId(id)
            .claimName("claimName")
            .claimValue("claimValue")
            .name("name");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided mappingRuleId exceeds the limit of 256 characters.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void deleteMappingShouldReturnNoContent() {
    // given
    final String mappingRuleId = "id";

    final var mappingRecord = new MappingRecord().setMappingRuleId(mappingRuleId);

    when(mappingServices.deleteMapping(mappingRuleId))
        .thenReturn(CompletableFuture.completedFuture(mappingRecord));

    // when
    webClient
        .delete()
        .uri("%s/%s".formatted(MAPPING_RULES_PATH, mappingRuleId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(mappingServices, times(1)).deleteMapping(mappingRuleId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "Foo", "foo123", "foo_", "foo.", "foo@"})
  void updateMappingShouldReturnOk(final String id) {
    // given
    final var dto = validUpdateMappingRequest(id);
    final var mappingRecord =
        new MappingRecord()
            .setMappingKey(1L)
            .setClaimName(dto.claimName())
            .setClaimValue(dto.claimValue())
            .setMappingRuleId(id)
            .setName(dto.name());

    when(mappingServices.updateMapping(dto))
        .thenReturn(CompletableFuture.completedFuture(mappingRecord));

    // when
    webClient
        .put()
        .uri(MAPPING_RULES_PATH + "/" + id)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    verify(mappingServices, times(1)).updateMapping(dto);
  }

  @Test
  void shouldRejectMappingUpdateWithMissingClaimName() {
    // given
    final var request = new MappingRuleUpdateRequest().claimValue("claimValue").name("name");

    // when then
    assertRequestRejectedExceptionally(
        "id",
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimName provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH + "/id"));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingUpdateWitBlankClaimName() {
    // given
    final var request =
        new MappingRuleUpdateRequest().claimName("").claimValue("claimValue").name("name");

    // when then
    assertRequestRejectedExceptionally(
        "id",
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimName provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH + "/id"));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingUpdateWithMissingClaimValue() {
    // given
    final var request = new MappingRuleUpdateRequest().claimName("claimName").name("name");

    // when then
    assertRequestRejectedExceptionally(
        "id",
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimValue provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH + "/id"));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingUpdateWitBlankClaimValue() {
    // given
    final var request =
        new MappingRuleUpdateRequest().claimName("claimName").claimValue("").name("name");

    // when then
    assertRequestRejectedExceptionally(
        "id",
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No claimValue provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH + "/id"));
    verifyNoInteractions(mappingServices);
  }

  @Test
  void shouldRejectMappingUpdateWithMissingName() {
    // given
    final var request =
        new MappingRuleUpdateRequest().claimName("claimName").claimValue("claimValue");

    // when then
    assertRequestRejectedExceptionally(
        "id",
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No name provided.",
              "instance": "%s"
            }"""
            .formatted(MAPPING_RULES_PATH + "/id"));
    verifyNoInteractions(mappingServices);
  }

  private MappingDTO validCreateMappingRequest() {
    return new MappingDTO("newClaimName", "newClaimValue", "mapName", "mapId");
  }

  private MappingDTO validUpdateMappingRequest(final String id) {
    return new MappingDTO("newClaimName", "newClaimValue", "mapName", id);
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

  private void assertRequestRejectedExceptionally(
      final String id, final MappingRuleUpdateRequest request, final String expectedError) {
    webClient
        .put()
        .uri(MAPPING_RULES_PATH + "/" + id)
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

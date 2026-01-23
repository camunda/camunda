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

import io.camunda.gateway.protocol.model.MappingRuleCreateRequest;
import io.camunda.gateway.protocol.model.MappingRuleUpdateRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(MappingRuleController.class)
public class MappingRuleControllerTest extends RestControllerTest {

  private static final String MAPPING_RULES_PATH = "/v2/mapping-rules";
  private static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);

  @MockitoBean private MappingRuleServices mappingRuleServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private SecurityConfiguration securityConfiguration;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(mappingRuleServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(mappingRuleServices);
    when(securityConfiguration.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "foo~", "Foo", "foo123", "foo_", "foo.", "foo@"})
  void createMappingRuleRuleShouldReturnCreated(final String id) {
    // given
    final var dto = validCreateMappingRuleRuleDTO();
    final var request =
        new MappingRuleCreateRequest(
            dto.mappingRuleId(), dto.claimName(), dto.claimValue(), dto.name());
    final var mappingRecord =
        new MappingRuleRecord()
            .setMappingRuleKey(1L)
            .setClaimName(dto.claimName())
            .setClaimValue(dto.claimValue())
            .setMappingRuleId(id)
            .setName(dto.name());

    when(mappingRuleServices.createMappingRule(dto))
        .thenReturn(CompletableFuture.completedFuture(mappingRecord));

    // when
    webClient
        .post()
        .uri(MAPPING_RULES_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(mappingRuleServices, times(1)).createMappingRule(dto);
  }

  @Test
  void shouldRejectMappingRuleCreationWithMissingId() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleCreationWitBlankId() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleCreationWithMissingClaimName() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleCreationWitBlankClaimName() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleCreationWithMissingClaimValue() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleCreationWitBlankClaimValue() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleCreationWithMissingName() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=", "foo{",
        "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'", "foo<", "foo>",
        "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
      })
  void shouldRejectMappingRuleCreationWithIllegalCharactersInId(final String id) {
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
            .formatted(SecurityConfiguration.DEFAULT_ID_REGEX, MAPPING_RULES_PATH));
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleWithTooLongId() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void deleteMappingRuleRuleShouldReturnNoContent() {
    // given
    final String mappingId = "id";

    final var mappingRecord = new MappingRuleRecord().setMappingRuleId(mappingId);

    when(mappingRuleServices.deleteMappingRule(mappingId))
        .thenReturn(CompletableFuture.completedFuture(mappingRecord));

    // when
    webClient
        .delete()
        .uri("%s/%s".formatted(MAPPING_RULES_PATH, mappingId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(mappingRuleServices, times(1)).deleteMappingRule(mappingId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "Foo", "foo123", "foo_", "foo.", "foo@"})
  void updateMappingRuleRuleShouldReturnOk(final String id) {
    // given
    final var dto = validUpdateMappingRuleRuleRequest(id);
    final var request =
        """
            {
              "claimName": "newClaimName",
              "claimValue": "newClaimValue",
              "name": "mapName"
            }""";
    final var mappingRecord =
        new MappingRuleRecord()
            .setMappingRuleKey(1L)
            .setClaimName(dto.claimName())
            .setClaimValue(dto.claimValue())
            .setMappingRuleId(id)
            .setName(dto.name());

    when(mappingRuleServices.updateMappingRule(dto))
        .thenReturn(CompletableFuture.completedFuture(mappingRecord));

    // when
    webClient
        .put()
        .uri(MAPPING_RULES_PATH + "/" + id)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    verify(mappingRuleServices, times(1)).updateMappingRule(dto);
  }

  @Test
  void shouldRejectMappingRuleUpdateWithMissingClaimName() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleUpdateWitBlankClaimName() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleUpdateWithMissingClaimValue() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleUpdateWitBlankClaimValue() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  @Test
  void shouldRejectMappingRuleUpdateWithMissingName() {
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
    verifyNoInteractions(mappingRuleServices);
  }

  private MappingRuleDTO validCreateMappingRuleRuleDTO() {
    return new MappingRuleDTO("newClaimName", "newClaimValue", "mapName", "mapRuleId");
  }

  private MappingRuleDTO validUpdateMappingRuleRuleRequest(final String id) {
    return new MappingRuleDTO("newClaimName", "newClaimValue", "mapName", id);
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
        .json(expectedError, JsonCompareMode.STRICT);
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
        .json(expectedError, JsonCompareMode.STRICT);
  }
}

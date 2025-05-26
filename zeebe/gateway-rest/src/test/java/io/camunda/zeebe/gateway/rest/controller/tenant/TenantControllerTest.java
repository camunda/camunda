/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.UserServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(TenantController.class)
public class TenantControllerTest extends RestControllerTest {

  private static final String TENANT_BASE_URL = "/v2/tenants";

  @MockBean private TenantServices tenantServices;
  @MockBean private UserServices userServices;
  @MockBean private MappingServices mappingServices;
  @MockBean private GroupServices groupServices;
  @MockBean private RoleServices roleServices;

  @BeforeEach
  void setup() {
    when(tenantServices.withAuthentication(any(Authentication.class))).thenReturn(tenantServices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "Foo", "foo123", "foo_", "foo.", "foo@"})
  void createTenantShouldReturnAccepted(final String id) {
    // given
    final var tenantName = "Test Tenant";
    final var tenantDescription = "Test description";
    when(tenantServices.createTenant(new TenantDTO(null, id, tenantName, tenantDescription)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord()
                    .setTenantKey(100L)
                    .setName(tenantName)
                    .setDescription(tenantDescription)
                    .setTenantId(id)));

    // when
    webClient
        .post()
        .uri(TENANT_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            new TenantCreateRequest().name(tenantName).description(tenantDescription).tenantId(id))
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(tenantServices, times(1))
        .createTenant(new TenantDTO(null, id, tenantName, tenantDescription));
  }

  @Test
  void createTenantShouldReturnAllDetails() {
    // given
    final var tenantName = "Test Tenant";
    final var tenantId = "tenantId";
    final var tenantDescription = "Test description";
    final var tenantKey = 100L;
    when(tenantServices.createTenant(new TenantDTO(null, tenantId, tenantName, tenantDescription)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord()
                    .setTenantKey(tenantKey)
                    .setName(tenantName)
                    .setDescription(tenantDescription)
                    .setTenantId(tenantId)));

    // when
    webClient
        .post()
        .uri(TENANT_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            new TenantCreateRequest()
                .name(tenantName)
                .tenantId(tenantId)
                .description(tenantDescription))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            {
              "tenantKey": "%d",
              "tenantId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                .formatted(tenantKey, tenantId, tenantName, tenantDescription));

    // then
    verify(tenantServices, times(1))
        .createTenant(new TenantDTO(null, tenantId, tenantName, tenantDescription));
  }

  @Test
  void createTenantWithEmptyTenantIdShouldFail() {
    // given
    final var tenantName = "Tenant Name";

    // when
    webClient
        .post()
        .uri(TENANT_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantCreateRequest().name(tenantName))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No tenantId provided.",
              "instance": "%s"
            }"""
                .formatted(TENANT_BASE_URL));

    // then
    verifyNoInteractions(tenantServices);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "foo~", "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=",
        "foo+", "foo{", "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'",
        "foo<", "foo>", "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
      })
  void shouldRejectTenantCreationWithIllegalCharactersInId(final String id) {
    // given
    final var tenantName = "Tenant Name";
    final var request = new TenantCreateRequest().tenantId(id).name(tenantName);

    // when
    webClient
        .post()
        .uri(TENANT_BASE_URL)
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
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                .formatted(IdentifierPatterns.ID_PATTERN, TENANT_BASE_URL));

    // then
    verifyNoInteractions(tenantServices);
  }

  @Test
  void shouldRejectTenantWithTooLongId() {
    // given
    final var id = "x".repeat(257);
    final var request = new TenantCreateRequest().tenantId(id).name("Tenant name");

    // when
    webClient
        .post()
        .uri(TENANT_BASE_URL)
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
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId exceeds the limit of 256 characters.",
              "instance": "%s"
            }"""
                .formatted(TENANT_BASE_URL));

    // then
    verifyNoInteractions(tenantServices);
  }

  @Test
  void updateTenantShouldReturnUpdatedResponse() {
    // given
    final var tenantKey = 100L;
    final var tenantName = "Updated Tenant Name";
    final var tenantId = "tenant-test-id";
    final var tenantDescription = "Updated description";
    when(tenantServices.updateTenant(new TenantDTO(null, tenantId, tenantName, tenantDescription)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord()
                    .setName(tenantName)
                    .setDescription(tenantDescription)
                    .setTenantKey(tenantKey)
                    .setTenantId(tenantId)));

    // when
    webClient
        .put()
        .uri("%s/%s".formatted(TENANT_BASE_URL, tenantId))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantUpdateRequest().name(tenantName).description(tenantDescription))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "tenantKey": "%d",
              "tenantId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                .formatted(tenantKey, tenantId, tenantName, tenantDescription));

    // then
    verify(tenantServices, times(1))
        .updateTenant(new TenantDTO(null, tenantId, tenantName, tenantDescription));
  }

  @Test
  void updateTenantWithoutDescriptionShouldFail() {
    // given
    final var tenantId = 100L;
    final var tenantName = "tenant name";
    final var uri = "%s/%s".formatted(TENANT_BASE_URL, tenantId);

    // when / then
    webClient
        .put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantUpdateRequest().name(tenantName))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No description provided.",
              "instance": "%s"
            }"""
                .formatted(uri));

    verifyNoInteractions(tenantServices);
  }

  @Test
  void updateTenantWithoutNameShouldFail() {
    // given
    final var tenantId = 100L;
    final var tenantDescription = "Tenant description";
    final var uri = "%s/%s".formatted(TENANT_BASE_URL, tenantId);

    // when / then
    webClient
        .put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantUpdateRequest().description(tenantDescription))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No name provided.",
              "instance": "%s"
            }"""
                .formatted(uri));

    verifyNoInteractions(tenantServices);
  }

  @Test
  void updateNonExistingTenantShouldReturnError() {
    // given
    final var tenantId = "tenant-id";
    final var tenantName = "My tenant";
    final var tenantDescription = "My tenant description";
    final var tenantKey = 100L;
    final var path = "%s/%s".formatted(TENANT_BASE_URL, tenantId);
    when(tenantServices.updateTenant(new TenantDTO(null, tenantId, tenantName, tenantDescription)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        TenantIntent.UPDATE,
                        tenantKey,
                        RejectionType.NOT_FOUND,
                        "Tenant not found"))));

    // when / then
    webClient
        .put()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantUpdateRequest().name(tenantName).description(tenantDescription))
        .exchange()
        .expectStatus()
        .isNotFound();

    verify(tenantServices, times(1))
        .updateTenant(new TenantDTO(null, tenantId, tenantName, tenantDescription));
  }

  @Test
  void deleteTenantShouldReturnNoContent() {
    // given
    final String tenantId = "tenant-to-delete-id";

    final var tenantRecord = new TenantRecord().setTenantId(tenantId);

    when(tenantServices.deleteTenant(tenantId))
        .thenReturn(CompletableFuture.completedFuture(tenantRecord));

    // when
    webClient
        .delete()
        .uri(TENANT_BASE_URL + "/{tenantId}", tenantId)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(tenantServices, times(1)).deleteTenant(tenantId);
  }

  @ParameterizedTest
  @MethodSource("provideAddMemberByIdTestCases")
  void testAddMemberToTenantById(final EntityType entityType, final String entityPath) {
    // given
    final var tenantId = "some-tenant-id";
    final var entityId = "some-entity-id";
    final var request = new TenantMemberRequest(tenantId, entityId, entityType);

    when(tenantServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .put()
        .uri("%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(tenantServices, times(1)).addMember(request);
  }

  @ParameterizedTest
  @MethodSource("provideAddMemberByIdTestCases")
  void testAddMemberToTenantWithInvalidTenantId(
      final EntityType entityType, final String entityPath) {
    // given
    final var tenantId = "invalidTenantId!";
    final var entityId = "some-entity-id";
    final var request = new TenantMemberRequest(tenantId, entityId, entityType);

    when(tenantServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
    webClient
        .put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                .formatted(IdentifierPatterns.ID_PATTERN, uri));

    // then
    verifyNoInteractions(tenantServices);
  }

  @ParameterizedTest
  @MethodSource("provideAddMemberByIdTestCases")
  void testAddMemberToTenantWithInvalidEntityId(
      final EntityType entityType, final String entityPath, final String entityIdName) {
    // given
    final var tenantId = "some-tenant-id";
    final var entityId = "invalidEntityId!";
    final var request = new TenantMemberRequest(tenantId, entityId, entityType);

    when(tenantServices.addMember(request)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
    webClient
        .put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided %s contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                .formatted(entityIdName, IdentifierPatterns.ID_PATTERN, uri));

    // then
    verifyNoInteractions(tenantServices);
  }

  @ParameterizedTest
  @MethodSource("provideRemoveMemberByIdTestCases")
  void testRemoveMemberByIdFromTenant(final EntityType entityType, final String entityPath) {
    // given
    final var tenantId = "some-tenant-id";
    final var entityId = "entity-id";
    final var tenantMemberRequest = new TenantMemberRequest(tenantId, entityId, entityType);

    when(tenantServices.removeMember(tenantMemberRequest))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .delete()
        .uri("%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(tenantServices, times(1)).removeMember(tenantMemberRequest);
  }

  @ParameterizedTest
  @MethodSource("provideAddMemberByIdTestCases")
  void testRemoveMemberToTenantWithInvalidTenantId(
      final EntityType entityType, final String entityPath) {
    // given
    final var tenantId = "invalidTenantId!";
    final var entityId = "some-entity-id";
    final var request = new TenantMemberRequest(tenantId, entityId, entityType);

    when(tenantServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
    webClient
        .put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided tenantId contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                .formatted(IdentifierPatterns.ID_PATTERN, uri));

    // then
    verifyNoInteractions(tenantServices);
  }

  @ParameterizedTest
  @MethodSource("provideAddMemberByIdTestCases")
  void testRemoveMemberToTenantWithInvalidEntityId(
      final EntityType entityType, final String entityPath, final String entityIdName) {
    // given
    final var tenantId = "some-tenant-id";
    final var entityId = "invalidEntityId!";
    final var request = new TenantMemberRequest(tenantId, entityId, entityType);

    when(tenantServices.removeMember(request)).thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var uri = "%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId);
    webClient
        .put()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided %s contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
                .formatted(entityIdName, IdentifierPatterns.ID_PATTERN, uri));

    // then
    verifyNoInteractions(tenantServices);
  }

  private static Stream<Arguments> provideAddMemberByIdTestCases() {
    return Stream.of(
        Arguments.of(EntityType.USER, "users", "username"),
        Arguments.of(EntityType.MAPPING, "mapping-rules", "mappingId"),
        Arguments.of(EntityType.GROUP, "groups", "groupId"),
        Arguments.of(EntityType.ROLE, "roles", "roleId"),
        Arguments.of(EntityType.CLIENT, "clients", "clientId"));
  }

  private static Stream<Arguments> provideRemoveMemberByIdTestCases() {
    return Stream.of(
        Arguments.of(EntityType.USER, "users", "username"),
        Arguments.of(EntityType.MAPPING, "mapping-rules", "mappingId"),
        Arguments.of(EntityType.GROUP, "groups", "groupId"),
        Arguments.of(EntityType.ROLE, "roles", "roleId"),
        Arguments.of(EntityType.CLIENT, "clients", "clientId"));
  }
}

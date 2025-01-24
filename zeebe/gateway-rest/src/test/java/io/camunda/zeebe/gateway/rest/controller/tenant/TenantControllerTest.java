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
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.UserServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(TenantController.class)
public class TenantControllerTest extends RestControllerTest {

  private static final String TENANT_BASE_URL = "/v2/tenants";

  @MockBean private TenantServices tenantServices;

  @MockBean private UserServices userServices;

  @BeforeEach
  void setup() {
    when(tenantServices.withAuthentication(any(Authentication.class))).thenReturn(tenantServices);
  }

  @Test
  void createTenantShouldReturnAccepted() {
    // given
    final var tenantName = "Test Tenant";
    final var tenantId = "tenant-test-id";
    final var tenantDescription = "Test description";
    when(tenantServices.createTenant(new TenantDTO(tenantId, tenantName, tenantDescription)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord()
                    .setTenantKey(100L)
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
                .description(tenantDescription)
                .tenantId(tenantId))
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(tenantServices, times(1))
        .createTenant(new TenantDTO(tenantId, tenantName, tenantDescription));
  }

  @Test
  void createTenantShouldReturnAllDetails() {
    // given
    final var tenantName = "Test Tenant";
    final var tenantId = "tenant-test-id";
    final var tenantDescription = "Test description";
    final var tenantKey = 100L;
    when(tenantServices.createTenant(new TenantDTO(tenantId, tenantName, tenantDescription)))
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
              "tenantId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                .formatted(tenantId, tenantName, tenantDescription));

    // then
    verify(tenantServices, times(1))
        .createTenant(new TenantDTO(tenantId, tenantName, tenantDescription));
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

  @Test
  void updateTenantShouldReturnUpdatedResponse() {
    // given
    final var tenantKey = 100L;
    final var tenantName = "Updated Tenant Name";
    final var tenantId = "tenant-test-id";
    final var tenantDescription = "Updated description";
    when(tenantServices.updateTenant(new TenantDTO(tenantId, tenantName, tenantDescription)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord()
                    .setName(tenantName)
                    .setDescription(tenantDescription)
                    .setTenantKey(tenantKey)
                    .setTenantId(tenantId)));

    // when
    webClient
        .patch()
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
              "tenantId": "%s",
              "name": "%s",
              "description": "%s"
            }
            """
                .formatted(tenantId, tenantName, tenantDescription));

    // then
    verify(tenantServices, times(1))
        .updateTenant(new TenantDTO(tenantId, tenantName, tenantDescription));
  }

  @Test
  void updateTenantWithoutDescriptionShouldFail() {
    // given
    final var tenantId = 100L;
    final var tenantName = "tenant name";
    final var uri = "%s/%s".formatted(TENANT_BASE_URL, tenantId);

    // when / then
    webClient
        .patch()
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
        .patch()
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
    when(tenantServices.updateTenant(new TenantDTO(tenantId, tenantName, tenantDescription)))
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
        .patch()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantUpdateRequest().name(tenantName).description(tenantDescription))
        .exchange()
        .expectStatus()
        .isNotFound();

    verify(tenantServices, times(1))
        .updateTenant(new TenantDTO(tenantId, tenantName, tenantDescription));
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
  @MethodSource("provideAddMemberTestCases")
  void testAddMemberToTenant(final EntityType entityType, final String entityPath) {
    // given
    final var tenantId = "some-tenant-id";
    final var entityId = "some-entity-id";

    when(tenantServices.addMember(tenantId, entityType, entityId))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    webClient
        .put()
        .uri("%s/%s/%s/%s".formatted(TENANT_BASE_URL, tenantId, entityPath, entityId))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(tenantServices, times(1)).addMember(tenantId, entityType, entityId);
  }

  @ParameterizedTest
  @MethodSource("provideRemoveMemberTestCases")
  void testRemoveMemberFromTenant(final EntityType entityType, final String entityPath) {
    // given
    final var tenantId = "some-tenant-id";
    final var entityId = "entity-id";

    when(tenantServices.removeMember(tenantId, entityType, entityId))
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
    verify(tenantServices, times(1)).removeMember(tenantId, entityType, entityId);
  }

  private static Stream<Arguments> provideAddMemberTestCases() {
    return Stream.of(Arguments.of(EntityType.USER, "users"));
  }

  private static Stream<Arguments> provideRemoveMemberTestCases() {
    return Stream.of(Arguments.of(EntityType.USER, "users"));
  }
}

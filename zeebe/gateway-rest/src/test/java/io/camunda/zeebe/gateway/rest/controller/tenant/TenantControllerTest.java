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
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(TenantController.class)
public class TenantControllerTest extends RestControllerTest {

  private static final String TENANT_BASE_URL = "/v2/tenants";

  @MockBean private TenantServices tenantServices;

  @BeforeEach
  void setup() {
    when(tenantServices.withAuthentication(any(Authentication.class))).thenReturn(tenantServices);
  }

  @Test
  void createTenantShouldReturnAccepted() {
    // given
    final var tenantName = "Test Tenant";
    final var tenantId = "tenant-test-id";
    when(tenantServices.createTenant(new TenantDTO(null, tenantId, tenantName)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord().setTenantKey(100L).setName(tenantName).setTenantId(tenantId)));

    // when
    webClient
        .post()
        .uri(TENANT_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantCreateRequest().name(tenantName).tenantId(tenantId))
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(tenantServices, times(1)).createTenant(new TenantDTO(null, tenantId, tenantName));
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
    when(tenantServices.updateTenant(new TenantDTO(tenantKey, null, tenantName)))
        .thenReturn(
            CompletableFuture.completedFuture(
                new TenantRecord()
                    .setName(tenantName)
                    .setTenantKey(tenantKey)
                    .setTenantId(tenantId)));

    // when
    webClient
        .patch()
        .uri("%s/%s".formatted(TENANT_BASE_URL, tenantKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new TenantUpdateRequest().name(tenantName))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "key": %d,
              "tenantId": "%s",
              "name": "%s"
            }
            """
                .formatted(tenantKey, tenantId, tenantName));

    // then
    verify(tenantServices, times(1)).updateTenant(new TenantDTO(tenantKey, null, tenantName));
  }

  @Test
  void updateTenantWithEmptyNameShouldFail() {
    // given
    final var tenantId = 100L;
    final var tenantName = "";
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
              "detail": "No name provided.",
              "instance": "%s"
            }"""
                .formatted(uri));

    verifyNoInteractions(tenantServices);
  }

  @Test
  void updateNonExistingTenantShouldReturnError() {
    // given
    final var tenantKey = 100L;
    final var tenantName = "Updated Tenant Name";
    final var path = "%s/%s".formatted(TENANT_BASE_URL, tenantKey);
    when(tenantServices.updateTenant(new TenantDTO(tenantKey, null, tenantName)))
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
        .bodyValue(new TenantUpdateRequest().name(tenantName))
        .exchange()
        .expectStatus()
        .isNotFound();

    verify(tenantServices, times(1)).updateTenant(new TenantDTO(tenantKey, null, tenantName));
  }

  @Test
  void deleteUserShouldReturnNoContent() {
    // given
    final long key = 1234L;

    final var tenantRecord = new TenantRecord().setTenantKey(key);

    when(tenantServices.deleteTenant(key))
        .thenReturn(CompletableFuture.completedFuture(tenantRecord));

    // when
    webClient
        .delete()
        .uri(TENANT_BASE_URL + "/{key}", key)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(tenantServices, times(1)).deleteTenant(key);
  }
}

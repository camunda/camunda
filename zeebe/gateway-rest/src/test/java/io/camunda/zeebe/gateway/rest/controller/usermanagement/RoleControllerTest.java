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
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.RoleChangeset;
import io.camunda.zeebe.gateway.protocol.rest.RoleCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(RoleController.class)
public class RoleControllerTest extends RestControllerTest {

  private static final String ROLE_BASE_URL = "/v2/roles";

  @MockBean private RoleServices roleServices;

  @BeforeEach
  void setup() {
    when(roleServices.withAuthentication(any(Authentication.class))).thenReturn(roleServices);
  }

  @Test
  void createRoleShouldReturnCreated() {
    // given
    final var roleId = "testRoleId";
    final var roleName = "Test Role";
    final var description = "A role used for testing";
    final var request = new CreateRoleRequest(roleId, roleName, description);
    when(roleServices.createRole(request))
        .thenReturn(
            CompletableFuture.completedFuture(
                new RoleRecord().setEntityKey(100L).setName(roleName)));

    // when
    webClient
        .post()
        .uri(ROLE_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new RoleCreateRequest().roleId(roleId).name(roleName).description(description))
        .exchange()
        .expectStatus()
        .isCreated();

    // then
    verify(roleServices, times(1)).createRole(request);
  }

  @Test
  void createRoleWithEmptyNameShouldFail() {
    // given
    final var roleId = "roleId";
    final var roleName = "";

    // when
    webClient
        .post()
        .uri(ROLE_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new RoleCreateRequest().roleId(roleId).name(roleName))
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
                .formatted(ROLE_BASE_URL));

    // then
    verifyNoInteractions(roleServices);
  }

  @Test
  void updateRoleShouldReturnNoContent() {
    // given
    final var roleKey = 100L;
    final var roleName = "Updated Role Name";
    when(roleServices.updateRole(roleKey, roleName))
        .thenReturn(
            CompletableFuture.completedFuture(
                new RoleRecord().setEntityKey(100L).setName(roleName)));

    // when
    webClient
        .patch()
        .uri("%s/%s".formatted(ROLE_BASE_URL, roleKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new RoleUpdateRequest().changeset(new RoleChangeset().name(roleName)))
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(roleServices, times(1)).updateRole(roleKey, roleName);
  }

  @Test
  void updateRoleWithEmptyNameShouldFail() {
    // given
    final var roleKey = 100L;
    final var roleName = "";
    final var uri = "%s/%s".formatted(ROLE_BASE_URL, roleKey);

    // when / then
    webClient
        .patch()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new RoleUpdateRequest().changeset(new RoleChangeset().name(roleName)))
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

    verifyNoInteractions(roleServices);
  }

  @Test
  void updateNonExistingRoleShouldReturnError() {
    // given
    final var roleKey = 100L;
    final var roleName = "Updated Role Name";
    final var path = "%s/%s".formatted(ROLE_BASE_URL, roleKey);
    when(roleServices.updateRole(roleKey, roleName))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
                    new BrokerRejection(
                        RoleIntent.UPDATE, roleKey, RejectionType.NOT_FOUND, "Role not found"))));

    // when / then
    webClient
        .patch()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new RoleUpdateRequest().changeset(new RoleChangeset().name(roleName)))
        .exchange()
        .expectStatus()
        .isNotFound();

    verify(roleServices, times(1)).updateRole(roleKey, roleName);
  }

  @Test
  void deleteRoleShouldReturnNoContent() {
    // given
    final long roleKey = 100L;

    final var roleRecord = new RoleRecord().setRoleKey(roleKey);

    when(roleServices.deleteRole(roleKey))
        .thenReturn(CompletableFuture.completedFuture(roleRecord));

    // when
    webClient
        .delete()
        .uri("%s/%s".formatted(ROLE_BASE_URL, roleKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(roleServices, times(1)).deleteRole(roleKey);
  }
}

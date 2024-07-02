/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.automation.permissions.PermissionEnum;
import io.camunda.identity.automation.rolemanagement.model.Role;
import io.camunda.identity.automation.rolemanagement.service.RoleService;
import io.camunda.zeebe.gateway.protocol.rest.Permission;
import io.camunda.zeebe.gateway.protocol.rest.RoleRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleResponse;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.RoleController;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

@WebMvcTest(RoleController.class)
public class RoleControllerTest extends RestControllerTest {

  @MockBean private RoleService roleService;

  @Test
  void getRoleByIdShouldReturnExistingRole() {
    final Role role = new Role();
    role.setName("test");

    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName("test");

    when(roleService.findRoleByName("test")).thenReturn(role);

    webClient
        .get()
        .uri("/v2/roles/test")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(RoleResponse.class)
        .isEqualTo(roleResponse);
  }

  @Test
  void getRoleByIdThrows400WhenServiceThrowsIllegalArgumentException() {
    final String message = "message";
    when(roleService.findRoleByName("something")).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/roles/something"));

    webClient
        .get()
        .uri("/v2/roles/something")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void getRoleByIdThrows500WhenServiceThrowsRuntimeException() {
    final String message = "message";
    when(roleService.findRoleByName("something")).thenThrow(new RuntimeException(message));

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message);
    expectedBody.setTitle(RuntimeException.class.getName());
    expectedBody.setInstance(URI.create("/v2/roles/something"));

    webClient
        .get()
        .uri("/v2/roles/something")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void createRoleShouldCreateAndReturnNewRole() {
    final Role role = new Role();
    role.setName("test");
    role.setDescription("description");

    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName("test");
    roleResponse.setDescription("description");

    final RoleRequest roleRequest = new RoleRequest();
    roleRequest.setName("test");
    roleRequest.setDescription("description");

    when(roleService.createRole(any(Role.class))).thenReturn(role);

    webClient
        .post()
        .uri("/v2/roles")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(roleRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(RoleResponse.class)
        .isEqualTo(roleResponse);

    verify(roleService, times(1)).createRole(any(Role.class));
  }

  @Test
  void createRoleByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final Role role = new Role();
    role.setName("test");

    final RoleRequest roleRequest = new RoleRequest();
    roleRequest.setName("test");

    when(roleService.createRole(any(Role.class))).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/roles"));

    webClient
        .post()
        .uri("/v2/roles")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(roleRequest)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void createRoleWithUnknownPermissionShouldThrowException() {
    final var body =
        "{\"name\": \"test\", \"description\": \"Test role\", \"permissions\": [\"UNKNOWN\"]}";

    final String message = "Failed to read request";
    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/roles"));

    webClient
        .post()
        .uri("/v2/roles")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void createRoleWithPermissionShouldCorrectlyMapPermissions() {
    final var body =
        "{\"name\": \"test\", \"description\": \"Test role\", \"permissions\": [\"CREATE_ALL\", \"READ_ALL\"]}";

    final var expectedRole = new Role();
    expectedRole.setName("test");
    expectedRole.setDescription("description");
    expectedRole.setPermissions(Set.of(PermissionEnum.CREATE_ALL, PermissionEnum.READ_ALL));

    when(roleService.createRole(any(Role.class))).thenReturn(expectedRole);

    final var expectedResponse = new RoleResponse();
    expectedResponse.setName("test");
    expectedResponse.setDescription("description");
    expectedResponse.setPermissions(List.of(Permission.CREATE_ALL, Permission.READ_ALL));

    final var actualResponse =
        webClient
            .post()
            .uri("/v2/roles")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(RoleResponse.class)
            .returnResult()
            .getResponseBody();

    assertThat(actualResponse).isNotNull();
    assertThat(actualResponse.getName()).isEqualTo(expectedResponse.getName());
    assertThat(actualResponse.getDescription()).isEqualTo(expectedResponse.getDescription());
    assertThat(actualResponse.getPermissions()).containsAll(expectedResponse.getPermissions());
  }

  @Test
  void updateRoleShouldUpdateAndReturnUpdatedRole() {
    final Role role = new Role();
    role.setName("test");
    role.setDescription("updated");

    final Role updatedRole = new Role();
    updatedRole.setName("test");
    updatedRole.setDescription("updated");

    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName("test");
    roleResponse.setDescription("updated");

    final RoleRequest roleRequest = new RoleRequest();
    roleRequest.setName("test");
    roleRequest.setDescription("updated");

    when(roleService.updateRole(eq("test"), any(Role.class))).thenReturn(updatedRole);

    webClient
        .put()
        .uri("/v2/roles/test")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(roleRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(RoleResponse.class)
        .isEqualTo(roleResponse);

    verify(roleService, times(1)).updateRole(eq("test"), any(Role.class));
  }

  @Test
  void updateRoleByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final Role role = new Role();
    role.setName("test");

    final RoleRequest roleRequest = new RoleRequest();
    roleRequest.setName("test");

    when(roleService.updateRole(eq("test"), any(Role.class)))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/roles/test"));

    webClient
        .put()
        .uri("/v2/roles/test")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(roleRequest)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void deleteRoleShouldRemoveExistingRole() {

    webClient
        .delete()
        .uri("/v2/roles/test")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful();

    verify(roleService, times(1)).deleteRoleByName("test");
  }

  @Test
  void searchRolesShouldReturnAllRoles() {
    final Role role = new Role();
    role.setName("test");

    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName("test");

    final RoleSearchResponse roleSearchResponse = new RoleSearchResponse();
    roleSearchResponse.setItems(List.of(roleResponse));

    final SearchQueryRequest searchQueryRequest = new SearchQueryRequest();

    when(roleService.findAllRoles()).thenReturn(List.of(role));

    webClient
        .post()
        .uri("/v2/roles/search")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchQueryRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(RoleSearchResponse.class)
        .isEqualTo(roleSearchResponse);

    verify(roleService, times(1)).findAllRoles();
  }

  @Test
  void searchRolesWithoutRequestBodyShouldReturnAllRoles() {
    final Role role = new Role();
    role.setName("test");

    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName("test");

    final RoleSearchResponse roleSearchResponse = new RoleSearchResponse();
    roleSearchResponse.setItems(List.of(roleResponse));

    when(roleService.findAllRoles()).thenReturn(List.of(role));

    webClient
        .post()
        .uri("/v2/roles/search")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(RoleSearchResponse.class)
        .isEqualTo(roleSearchResponse);

    verify(roleService, times(1)).findAllRoles();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.PatchAuthorizationRequest;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest.ActionEnum;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequestPermissionsInner;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequestPermissionsInner.PermissionTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.AuthorizationController;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionAction;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

@WebMvcTest(AuthorizationController.class)
public class AuthorizationControllerTest extends RestControllerTest {

  @MockBean private AuthorizationServices<AuthorizationRecord> authorizationServices;

  @BeforeEach
  void setup() {
    when(authorizationServices.withAuthentication(any(Authentication.class)))
        .thenReturn(authorizationServices);
  }

  @Test
  void patchAuthorizationShouldReturnNoContent() {
    final var ownerKey = 1L;
    final var action = ActionEnum.ADD;
    final var resourceIds = List.of("permission1", "permission2");
    final var permissions =
        new AuthorizationPatchRequestPermissionsInner()
            .permissionType(PermissionTypeEnum.CREATE)
            .resourceIds(resourceIds);
    final var request =
        new AuthorizationPatchRequest()
            .action(action)
            .resourceType(ResourceTypeEnum.DEPLOYMENT)
            .permissions(List.of(permissions));

    final var permission =
        new Permission().setPermissionType(PermissionType.CREATE).addResourceIds(resourceIds);
    final var authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(ownerKey)
            .setOwnerType(AuthorizationOwnerType.USER)
            .setAction(PermissionAction.valueOf(request.getAction().name()))
            .setResourceType(AuthorizationResourceType.valueOf(request.getResourceType().name()))
            .addPermission(permission);

    when(authorizationServices.patchAuthorization(any(PatchAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(authorizationRecord));

    webClient
        .patch()
        .uri("/v2/authorizations/%d".formatted(ownerKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    final var captor = ArgumentCaptor.forClass(PatchAuthorizationRequest.class);
    verify(authorizationServices, times(1)).patchAuthorization(captor.capture());
    final var capturedRequest = captor.getValue();
    assertEquals(capturedRequest.ownerKey(), authorizationRecord.getOwnerKey());
    assertEquals(capturedRequest.action(), authorizationRecord.getAction());
    assertEquals(capturedRequest.resourceType(), authorizationRecord.getResourceType());
    assertEquals(capturedRequest.permissions().size(), 1);
    assertEquals(
        capturedRequest.permissions().get(permission.getPermissionType()),
        permission.getResourceIds());
  }

  @Test
  void patchAuthorizationThrowsExceptionWhenServiceThrowsException() {
    final var ownerKey = 1L;
    final var action = ActionEnum.ADD;
    final var resourceIds = List.of("permission1", "permission2");
    final var permissions =
        new AuthorizationPatchRequestPermissionsInner()
            .permissionType(PermissionTypeEnum.CREATE)
            .resourceIds(resourceIds);
    final var request =
        new AuthorizationPatchRequest()
            .action(action)
            .resourceType(ResourceTypeEnum.DEPLOYMENT)
            .permissions(List.of(permissions));

    when(authorizationServices.patchAuthorization(any(PatchAuthorizationRequest.class)))
        .thenThrow(
            new CamundaBrokerException(
                new BrokerRejection(
                    AuthorizationIntent.ADD_PERMISSION,
                    1L,
                    RejectionType.ALREADY_EXISTS,
                    "Authorization already exists")));

    final var expectedBody = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/authorizations/%d".formatted(ownerKey)));

    webClient
        .patch()
        .uri("/v2/authorizations/%d".formatted(ownerKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @ParameterizedTest
  @MethodSource("provideInvalidRequests")
  public void patchAuthorizationShouldReturnBadRequest(
      final AuthorizationPatchRequest request, final String errorMessage) {
    final var ownerKey = 1L;
    final var expectedBody = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(INVALID_ARGUMENT.name());
    expectedBody.setInstance(URI.create("/v2/authorizations/%d".formatted(ownerKey)));
    expectedBody.setDetail(errorMessage);

    webClient
        .patch()
        .uri("/v2/authorizations/%d".formatted(ownerKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  private static Stream<Arguments> provideInvalidRequests() {
    final var resourceIds = List.of("permission1", "permission2");
    final var validPermissions =
        List.of(
            new AuthorizationPatchRequestPermissionsInner()
                .permissionType(PermissionTypeEnum.CREATE)
                .resourceIds(resourceIds));

    return Stream.of(
        Arguments.of(
            new AuthorizationPatchRequest()
                .resourceType(ResourceTypeEnum.DEPLOYMENT)
                .permissions(validPermissions),
            "No action provided."),
        Arguments.of(
            new AuthorizationPatchRequest().action(ActionEnum.ADD).permissions(validPermissions),
            "No resourceType provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.DEPLOYMENT),
            "No permissions provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.DEPLOYMENT)
                .permissions(List.of()),
            "No permissions provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.DEPLOYMENT)
                .permissions(
                    List.of(
                        new AuthorizationPatchRequestPermissionsInner()
                            .resourceIds(List.of("resourceId")))),
            "No permissionType provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.DEPLOYMENT)
                .permissions(
                    List.of(
                        new AuthorizationPatchRequestPermissionsInner()
                            .permissionType(PermissionTypeEnum.CREATE))),
            "No resourceIds provided in 'CREATE'."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.DEPLOYMENT)
                .permissions(
                    List.of(
                        new AuthorizationPatchRequestPermissionsInner()
                            .permissionType(PermissionTypeEnum.CREATE)
                            .resourceIds(List.of()))),
            "No resourceIds provided in 'CREATE'."));
  }
}

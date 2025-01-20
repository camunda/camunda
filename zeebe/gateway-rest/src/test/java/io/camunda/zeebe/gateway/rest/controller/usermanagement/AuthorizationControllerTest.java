/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.PatchAuthorizationRequest;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationPatchRequest.ActionEnum;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.PermissionDTO;
import io.camunda.zeebe.gateway.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.net.URI;
import java.util.List;
import java.util.Set;
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

  @MockBean private AuthorizationServices authorizationServices;

  @BeforeEach
  void setup() {
    when(authorizationServices.withAuthentication(any(Authentication.class)))
        .thenReturn(authorizationServices);
  }

  @Test
  void createAuthorizationShouldReturnTheKey() {
    final var authorizationKey = 1L;
    final var ownerId = "ownerId";
    final var resourceId = "resourceId";

    final var request =
        new AuthorizationCreateRequest()
            .ownerId(ownerId)
            .ownerType(OwnerTypeEnum.USER)
            .resourceId(resourceId)
            .resourceType(ResourceTypeEnum.PROCESS_DEFINITION)
            .permissions(List.of(PermissionTypeEnum.CREATE));

    final var authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(AuthorizationOwnerType.USER)
            .setResourceId(resourceId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setAuthorizationPermissions(Set.of(PermissionType.CREATE));

    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(authorizationRecord));

    webClient
        .post()
        .uri("/v2/authorizations")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            {
              "authorizationKey": "%s"
            }"""
                .formatted(authorizationKey));

    final var captor = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(1)).createAuthorization(captor.capture());
    final var capturedRequest = captor.getValue();
    assertEquals(ownerId, capturedRequest.ownerId());
    assertEquals(authorizationRecord.getOwnerType(), capturedRequest.ownerType());
    assertEquals(resourceId, capturedRequest.resourceId());
    assertEquals(authorizationRecord.getResourceType(), capturedRequest.resourceType());
    assertEquals(1, capturedRequest.permissions().size());
    assertEquals(authorizationRecord.getAuthorizationPermissions(), capturedRequest.permissions());
  }

  @ParameterizedTest
  @MethodSource("provideInvalidCreationRequests")
  public void createAuthorizationShouldReturnBadRequest(
      final AuthorizationCreateRequest request, final String errorMessage) {
    final var expectedBody = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(INVALID_ARGUMENT.name());
    expectedBody.setInstance(URI.create("/v2/authorizations"));
    expectedBody.setDetail(errorMessage);

    webClient
        .post()
        .uri("/v2/authorizations")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  private static Stream<Arguments> provideInvalidCreationRequests() {
    final var permissions = List.of(PermissionTypeEnum.CREATE);

    return Stream.of(
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(permissions),
            "No ownerId provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(permissions),
            "No ownerId provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("ownerId")
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(permissions),
            "No ownerType provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(permissions),
            "No resourceId provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(permissions),
            "No resourceId provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .permissions(permissions),
            "No resourceType provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE),
            "No permissions provided."),
        Arguments.of(
            new AuthorizationCreateRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(List.of()),
            "No permissions provided."));
  }

  @Test
  void deleteAuthorizationShouldReturnNoContent() {
    // given
    final long authorizationKey = 100L;

    final var record = new AuthorizationRecord().setAuthorizationKey(authorizationKey);

    when(authorizationServices.deleteAuthorization(authorizationKey))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when
    webClient
        .delete()
        .uri("/v2/authorizations/%s".formatted(authorizationKey))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(authorizationServices, times(1)).deleteAuthorization(authorizationKey);
  }

  @Test
  void patchAuthorizationShouldReturnNoContent() {
    final var ownerKey = 1L;
    final var action = ActionEnum.ADD;
    final var resourceIds = Set.of("permission1", "permission2");
    final var permissions =
        new PermissionDTO().permissionType(PermissionTypeEnum.CREATE).resourceIds(resourceIds);
    final var request =
        new AuthorizationPatchRequest()
            .action(action)
            .resourceType(ResourceTypeEnum.RESOURCE)
            .permissions(List.of(permissions));

    final var permission =
        new Permission().setPermissionType(PermissionType.CREATE).addResourceIds(resourceIds);
    final var authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(ownerKey)
            .setOwnerType(AuthorizationOwnerType.USER)
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
    assertEquals(capturedRequest.resourceType(), authorizationRecord.getResourceType());
    assertEquals(1, capturedRequest.permissions().size());
    assertEquals(
        capturedRequest.permissions().get(permission.getPermissionType()),
        permission.getResourceIds());
  }

  @Test
  void patchAuthorizationThrowsExceptionWhenServiceThrowsException() {
    final var ownerKey = 1L;
    final var action = ActionEnum.ADD;
    final var resourceIds = Set.of("permission1", "permission2");
    final var permissions =
        new PermissionDTO().permissionType(PermissionTypeEnum.CREATE).resourceIds(resourceIds);
    final var request =
        new AuthorizationPatchRequest()
            .action(action)
            .resourceType(ResourceTypeEnum.RESOURCE)
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
    final var resourceIds = Set.of("permission1", "permission2");
    final var validPermissions =
        List.of(
            new PermissionDTO().permissionType(PermissionTypeEnum.CREATE).resourceIds(resourceIds));

    return Stream.of(
        Arguments.of(
            new AuthorizationPatchRequest()
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(validPermissions),
            "No action provided."),
        Arguments.of(
            new AuthorizationPatchRequest().action(ActionEnum.ADD).permissions(validPermissions),
            "No resourceType provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.RESOURCE),
            "No permissions provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(List.of()),
            "No permissions provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(List.of(new PermissionDTO().resourceIds(Set.of("resourceId")))),
            "No permissionType provided."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(
                    List.of(new PermissionDTO().permissionType(PermissionTypeEnum.CREATE))),
            "No resourceIds provided in 'CREATE'."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissions(
                    List.of(
                        new PermissionDTO()
                            .permissionType(PermissionTypeEnum.CREATE)
                            .resourceIds(Set.of()))),
            "No resourceIds provided in 'CREATE'."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.USER)
                .permissions(
                    List.of(
                        new PermissionDTO()
                            .permissionType(PermissionTypeEnum.DELETE_PROCESS)
                            .resourceIds(Set.of("resourceId")))),
            "Permission type 'DELETE_PROCESS' is allowed for resource type 'USER'."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.USER)
                .permissions(
                    List.of(
                        new PermissionDTO()
                            .permissionType(PermissionTypeEnum.DELETE_DRD)
                            .resourceIds(Set.of("resourceId")))),
            "Permission type 'DELETE_DRD' is allowed for resource type 'USER'."),
        Arguments.of(
            new AuthorizationPatchRequest()
                .action(ActionEnum.ADD)
                .resourceType(ResourceTypeEnum.USER)
                .permissions(
                    List.of(
                        new PermissionDTO()
                            .permissionType(PermissionTypeEnum.DELETE_FORM)
                            .resourceIds(Set.of("resourceId")))),
            "Permission type 'DELETE_FORM' is allowed for resource type 'USER'."));
  }
}

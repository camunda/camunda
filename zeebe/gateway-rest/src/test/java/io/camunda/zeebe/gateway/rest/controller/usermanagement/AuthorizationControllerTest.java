/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationRequest;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AuthorizationController.class)
public class AuthorizationControllerTest extends RestControllerTest {

  @MockitoBean private AuthorizationServices authorizationServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authorizationServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(authorizationServices);
  }

  @Test
  void createAuthorizationShouldReturnTheKey() {
    final var authorizationKey = 1L;
    final var ownerId = "ownerId";
    final var resourceId = "resourceId";

    final var request =
        new AuthorizationRequest()
            .ownerId(ownerId)
            .ownerType(OwnerTypeEnum.USER)
            .resourceId(resourceId)
            .resourceType(ResourceTypeEnum.PROCESS_DEFINITION)
            .permissionTypes(List.of(PermissionTypeEnum.CREATE));

    final var authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(AuthorizationOwnerType.USER)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setPermissionTypes(Set.of(PermissionType.CREATE));

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
                .formatted(authorizationKey),
            JsonCompareMode.STRICT);

    final var captor = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(1)).createAuthorization(captor.capture());
    final var capturedRequest = captor.getValue();
    assertThat(capturedRequest.ownerId()).isEqualTo(ownerId);
    assertThat(capturedRequest.ownerType()).isEqualTo(authorizationRecord.getOwnerType());
    assertThat(capturedRequest.resourceId()).isEqualTo(resourceId);
    assertThat(capturedRequest.resourceType()).isEqualTo(authorizationRecord.getResourceType());
    assertThat(capturedRequest.permissionTypes().size()).isEqualTo(1);
    assertThat(capturedRequest.permissionTypes())
        .isEqualTo(authorizationRecord.getPermissionTypes());
  }

  @ParameterizedTest
  @MethodSource("provideInvalidAuthorizationRequests")
  public void createAuthorizationShouldReturnBadRequest(
      final AuthorizationRequest request, final String errorMessage) {
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
  void updateAuthorizationShouldReturnNoContent() {
    // given
    final long authorizationKey = 100L;
    final var ownerId = "ownerId";
    final var resourceId = "resourceId";

    final var request =
        new AuthorizationRequest()
            .ownerId(ownerId)
            .ownerType(OwnerTypeEnum.USER)
            .resourceId(resourceId)
            .resourceType(ResourceTypeEnum.PROCESS_DEFINITION)
            .permissionTypes(List.of(PermissionTypeEnum.CREATE));

    final var authorizationRecord =
        new AuthorizationRecord()
            .setAuthorizationKey(authorizationKey)
            .setOwnerId(ownerId)
            .setOwnerType(AuthorizationOwnerType.USER)
            .setResourceMatcher(AuthorizationResourceMatcher.ID)
            .setResourceId(resourceId)
            .setResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .setPermissionTypes(Set.of(PermissionType.CREATE));

    when(authorizationServices.updateAuthorization(any()))
        .thenReturn(CompletableFuture.completedFuture(authorizationRecord));

    // when
    webClient
        .put()
        .uri("/v2/authorizations/%s".formatted(authorizationKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    final var captor = ArgumentCaptor.forClass(UpdateAuthorizationRequest.class);
    verify(authorizationServices, times(1)).updateAuthorization(captor.capture());
    final var capturedRequest = captor.getValue();
    assertThat(capturedRequest.authorizationKey()).isEqualTo(authorizationKey);
    assertThat(capturedRequest.ownerId()).isEqualTo(ownerId);
    assertThat(capturedRequest.ownerType()).isEqualTo(authorizationRecord.getOwnerType());
    assertThat(capturedRequest.resourceId()).isEqualTo(resourceId);
    assertThat(capturedRequest.resourceType()).isEqualTo(authorizationRecord.getResourceType());
    assertThat(capturedRequest.permissionTypes().size()).isEqualTo(1);
    assertThat(capturedRequest.permissionTypes())
        .isEqualTo(authorizationRecord.getPermissionTypes());
  }

  @ParameterizedTest
  @MethodSource("provideInvalidAuthorizationRequests")
  public void updateAuthorizationShouldReturnBadRequest(
      final AuthorizationRequest request, final String errorMessage) {
    final var expectedBody = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(INVALID_ARGUMENT.name());
    expectedBody.setInstance(URI.create("/v2/authorizations/1"));
    expectedBody.setDetail(errorMessage);

    webClient
        .put()
        .uri("/v2/authorizations/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  private static Stream<Arguments> provideInvalidAuthorizationRequests() {
    final var permissions = List.of(PermissionTypeEnum.CREATE);

    return Stream.of(
        Arguments.of(
            new AuthorizationRequest()
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissionTypes(permissions),
            "No ownerId provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissionTypes(permissions),
            "No ownerId provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("ownerId")
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissionTypes(permissions),
            "No ownerType provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissionTypes(permissions),
            "No resourceId provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissionTypes(permissions),
            "No resourceId provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .permissionTypes(permissions),
            "No resourceType provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE),
            "No permissionTypes provided."),
        Arguments.of(
            new AuthorizationRequest()
                .ownerId("ownerId")
                .ownerType(OwnerTypeEnum.USER)
                .resourceId("resourceId")
                .resourceType(ResourceTypeEnum.RESOURCE)
                .permissionTypes(List.of()),
            "No permissionTypes provided."));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.AuthorizationServices;
import io.camunda.service.CamundaServiceException;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationAssignRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationAssignRequest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.AuthorizationController;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  void createAuthorizationShouldReturnNoContent() {
    final var request =
        new AuthorizationAssignRequest()
            .ownerKey(1L)
            .ownerType(OwnerTypeEnum.USER)
            .resourceKey("2")
            .resourceType("resourceType")
            .permissions(List.of("permission1", "permission2"));

    final var authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(request.getOwnerKey())
            .setOwnerType(AuthorizationOwnerType.valueOf(request.getOwnerType().getValue()))
            .setResourceKey(request.getResourceKey())
            .setResourceType(request.getResourceType())
            .setPermissions(request.getPermissions());

    when(authorizationServices.createAuthorization(
            request.getOwnerKey(),
            AuthorizationOwnerType.valueOf(request.getOwnerType().getValue()),
            request.getResourceKey(),
            request.getResourceType(),
            request.getPermissions()))
        .thenReturn(CompletableFuture.completedFuture(authorizationRecord));

    webClient
        .post()
        .uri("/v2/authorizations")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(authorizationServices, times(1))
        .createAuthorization(
            request.getOwnerKey(),
            AuthorizationOwnerType.valueOf(request.getOwnerType().getValue()),
            request.getResourceKey(),
            request.getResourceType(),
            request.getPermissions());
  }

  @Test
  void createAuthorizationThrowsExceptionWhenServiceThrowsException() {
    final var request =
        new AuthorizationAssignRequest()
            .ownerKey(1L)
            .ownerType(OwnerTypeEnum.USER)
            .resourceKey("2")
            .resourceType("resourceType")
            .permissions(List.of("permission1", "permission2"));

    when(authorizationServices.createAuthorization(
            request.getOwnerKey(),
            AuthorizationOwnerType.valueOf(request.getOwnerType().getValue()),
            request.getResourceKey(),
            request.getResourceType(),
            request.getPermissions()))
        .thenThrow(
            new CamundaServiceException(
                new BrokerRejection(
                    AuthorizationIntent.CREATE,
                    1L,
                    RejectionType.ALREADY_EXISTS,
                    "Authorization already exists")));

    final var expectedBody = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/authorizations"));

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

  @ParameterizedTest
  @MethodSource("provideInvalidRequests")
  public void createAuthorizationShouldReturnBadRequest(
      final AuthorizationAssignRequest request, final String errorMessage) {
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

  private static Stream<Arguments> provideInvalidRequests() {
    return Stream.of(
        Arguments.of(
            new AuthorizationAssignRequest()
                .ownerKey(null)
                .ownerType(OwnerTypeEnum.USER)
                .resourceKey("2")
                .resourceType("resourceType")
                .permissions(null),
            "No ownerKey provided."),
        Arguments.of(
            new AuthorizationAssignRequest()
                .ownerKey(1L)
                .ownerType(null)
                .resourceKey("2")
                .resourceType("resourceType")
                .permissions(null),
            "No ownerType provided."),
        Arguments.of(
            new AuthorizationAssignRequest()
                .ownerKey(1L)
                .ownerType(OwnerTypeEnum.USER)
                .resourceKey(null)
                .resourceType("resourceType")
                .permissions(null),
            "No resourceKey provided."),
        Arguments.of(
            new AuthorizationAssignRequest()
                .ownerKey(1L)
                .ownerType(OwnerTypeEnum.USER)
                .resourceKey("    ")
                .resourceType("resourceType")
                .permissions(null),
            "No resourceKey provided."),
        Arguments.of(
            new AuthorizationAssignRequest()
                .ownerKey(1L)
                .ownerType(OwnerTypeEnum.USER)
                .resourceKey("2")
                .resourceType(null)
                .permissions(null),
            "No resourceType provided."),
        Arguments.of(
            new AuthorizationAssignRequest()
                .ownerKey(1L)
                .ownerType(OwnerTypeEnum.USER)
                .resourceKey("2")
                .resourceType("    ")
                .permissions(null),
            "No resourceType provided."));
  }
}

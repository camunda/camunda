/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationCreateContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationIdBasedRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationPropertyBasedRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.AuthorizationRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.OwnerTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.PermissionTypeEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.ResourceTypeEnum;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.AuthorizationServices.UpdateAuthorizationRequest;
import io.camunda.zeebe.gateway.rest.CamundaProblemDetail;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.AuthorizationController;
import io.camunda.zeebe.gateway.rest.controller.adapter.DefaultAuthorizationServiceAdapter;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import({DefaultAuthorizationServiceAdapter.class, SecurityConfiguration.class})
@WebMvcTest(AuthorizationController.class)
public class AuthorizationControllerTest extends RestControllerTest {

  @MockitoBean private AuthorizationServices authorizationServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authenticationProvider.getAnonymousIfUnavailable())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @ParameterizedTest
  @MethodSource("provideValidCreateAuthorizationRequests")
  void createAuthorizationShouldReturnTheKey(
      final AuthorizationRequestContract providedRequest,
      final CreateAuthorizationRequest expectedCreateRequest) {
    final var authorizationKey = 1L;

    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new AuthorizationRecord().setAuthorizationKey(authorizationKey)));

    webClient
        .post()
        .uri("/v2/authorizations")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(providedRequest)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(AuthorizationCreateContract.class)
        .isEqualTo(new AuthorizationCreateContract(String.valueOf(authorizationKey)));

    final var captor = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices).createAuthorization(captor.capture(), any());
    final var capturedCreateRequest = captor.getValue();
    assertThat(capturedCreateRequest).isEqualTo(expectedCreateRequest);
  }

  @ParameterizedTest
  @MethodSource("provideInvalidAuthorizationRequests")
  public void createAuthorizationShouldReturnBadRequest(
      final Object request, final String errorMessage, final String expectedTitle) {
    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(expectedTitle);
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
  void createAuthorizationShouldReturnBadRequestWhenBothResourceIdAndPropertyNameProvided() {
    // given
    final var request =
        """
            {
              "ownerType": "USER",
              "resourceType": "RESOURCE",
              "resourceId": "resourceId",
              "resourcePropertyName": "propertyName",
              "permissionTypes":["CREATE"]
            }""";

    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/authorizations"));
    expectedBody.setDetail("No ownerId provided.");

    // when - then
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
  void createAuthorizationShouldReturnBadRequestWhenNoResourceIdNorPropertyNameProvided() {
    // given
    final var request =
        """
            {
              "ownerType": "USER",
              "resourceType": "RESOURCE",
              "permissionTypes":["CREATE"]
            }""";

    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/authorizations"));
    expectedBody.setDetail("At least one of [resourceId, resourcePropertyName] is required");

    // when - then
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

    when(authorizationServices.deleteAuthorization(eq(authorizationKey), any()))
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
    verify(authorizationServices).deleteAuthorization(eq(authorizationKey), any());
  }

  @ParameterizedTest
  @MethodSource("provideValidUpdateAuthorizationRequests")
  void updateAuthorizationShouldReturnNoContent(
      final AuthorizationRequestContract providedRequest,
      final UpdateAuthorizationRequest expectedUpdateRequest) {
    // given
    final long authorizationKey = expectedUpdateRequest.authorizationKey();

    when(authorizationServices.updateAuthorization(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    webClient
        .put()
        .uri("/v2/authorizations/%s".formatted(authorizationKey))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(providedRequest)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    final var captor = ArgumentCaptor.forClass(UpdateAuthorizationRequest.class);
    verify(authorizationServices).updateAuthorization(captor.capture(), any());
    final var capturedUpdateRequest = captor.getValue();
    assertThat(capturedUpdateRequest).isEqualTo(expectedUpdateRequest);
  }

  @ParameterizedTest
  @MethodSource("provideInvalidAuthorizationRequests")
  public void updateAuthorizationShouldReturnBadRequest(
      final Object request, final String errorMessage, final String expectedTitle) {
    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle(expectedTitle);
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

  @Test
  void updateAuthorizationShouldReturnBadRequestWhenBothResourceIdAndPropertyNameProvided() {
    // given
    final var request =
        """
            {
              "ownerType": "USER",
              "resourceType": "USER_TASK",
              "resourceId": "123",
              "resourcePropertyName": "assignee",
              "permissionTypes":["UPDATE"]
            }""";

    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/authorizations/2"));
    expectedBody.setDetail("No ownerId provided.");

    // when - then
    webClient
        .put()
        .uri("/v2/authorizations/2")
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
  void updateAuthorizationShouldReturnBadRequestWhenNoResourceIdNorPropertyNameProvided() {
    // given
    final var request =
        """
            {
              "ownerType": "USER",
              "resourceType": "USER_TASK",
              "permissionTypes":["UPDATE"]
            }""";

    final var expectedBody = CamundaProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/v2/authorizations/3"));
    expectedBody.setDetail("At least one of [resourceId, resourcePropertyName] is required");

    // when - then
    webClient
        .put()
        .uri("/v2/authorizations/3")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  private static Stream<Arguments> provideValidCreateAuthorizationRequests() {
    final var permissionEnums = List.of(PermissionTypeEnum.READ);
    final var permissions = Set.of(PermissionType.READ);

    return Stream.of(
        Arguments.of(
            // Id request
            new AuthorizationIdBasedRequestContract(
                "ownerId",
                OwnerTypeEnum.USER,
                "resourceId",
                ResourceTypeEnum.RESOURCE,
                permissionEnums),
            new CreateAuthorizationRequest(
                "ownerId",
                AuthorizationOwnerType.USER,
                AuthorizationResourceMatcher.ID,
                "resourceId",
                "",
                AuthorizationResourceType.RESOURCE,
                permissions)),
        Arguments.of(
            // Id (ANY) request
            new AuthorizationIdBasedRequestContract(
                "ownerId", OwnerTypeEnum.USER, "*", ResourceTypeEnum.RESOURCE, permissionEnums),
            new CreateAuthorizationRequest(
                "ownerId",
                AuthorizationOwnerType.USER,
                AuthorizationResourceMatcher.ANY,
                "*",
                "",
                AuthorizationResourceType.RESOURCE,
                permissions)),
        Arguments.of(
            // Property request
            new AuthorizationPropertyBasedRequestContract(
                "ownerId",
                OwnerTypeEnum.USER,
                "assignee",
                ResourceTypeEnum.USER_TASK,
                permissionEnums),
            new CreateAuthorizationRequest(
                "ownerId",
                AuthorizationOwnerType.USER,
                AuthorizationResourceMatcher.PROPERTY,
                "",
                "assignee",
                AuthorizationResourceType.USER_TASK,
                permissions)));
  }

  private static Stream<Arguments> provideValidUpdateAuthorizationRequests() {
    final var permissionEnums = List.of(PermissionTypeEnum.READ);
    final var permissions = Set.of(PermissionType.READ);

    return Stream.of(
        Arguments.of(
            // Id request
            new AuthorizationIdBasedRequestContract(
                "ownerId",
                OwnerTypeEnum.USER,
                "resourceId",
                ResourceTypeEnum.RESOURCE,
                permissionEnums),
            new UpdateAuthorizationRequest(
                100,
                "ownerId",
                AuthorizationOwnerType.USER,
                AuthorizationResourceMatcher.ID,
                "resourceId",
                "",
                AuthorizationResourceType.RESOURCE,
                permissions)),
        Arguments.of(
            // Id (ANY) request
            new AuthorizationIdBasedRequestContract(
                "ownerId", OwnerTypeEnum.USER, "*", ResourceTypeEnum.RESOURCE, permissionEnums),
            new UpdateAuthorizationRequest(
                200,
                "ownerId",
                AuthorizationOwnerType.USER,
                AuthorizationResourceMatcher.ANY,
                "*",
                "",
                AuthorizationResourceType.RESOURCE,
                permissions)),
        Arguments.of(
            // Property request
            new AuthorizationPropertyBasedRequestContract(
                "ownerId",
                OwnerTypeEnum.USER,
                "assignee",
                ResourceTypeEnum.USER_TASK,
                permissionEnums),
            new UpdateAuthorizationRequest(
                300,
                "ownerId",
                AuthorizationOwnerType.USER,
                AuthorizationResourceMatcher.PROPERTY,
                "",
                "assignee",
                AuthorizationResourceType.USER_TASK,
                permissions)));
  }

  private static Stream<Arguments> provideInvalidAuthorizationRequests() {
    final var permissions = List.of(PermissionTypeEnum.CREATE);

    return Stream.of(
        // AuthorizationIdBasedRequest tests
        Arguments.of(
            "{\"ownerType\":\"USER\",\"resourceId\":\"resourceId\",\"resourceType\":\"RESOURCE\",\"permissionTypes\":[\"CREATE\"]}",
            "No ownerId provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationIdBasedRequestContract(
                "", OwnerTypeEnum.USER, "resourceId", ResourceTypeEnum.RESOURCE, permissions),
            "No ownerId provided.",
            "Bad Request"),
        Arguments.of(
            "{\"ownerId\":\"ownerId\",\"resourceId\":\"resourceId\",\"resourceType\":\"RESOURCE\",\"permissionTypes\":[\"CREATE\"]}",
            "No ownerType provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationIdBasedRequestContract(
                "ownerId", OwnerTypeEnum.USER, "", ResourceTypeEnum.RESOURCE, permissions),
            "Either resourceId or resourcePropertyName must be provided.",
            "Bad Request"),
        Arguments.of(
            "{\"ownerId\":\"ownerId\",\"ownerType\":\"USER\",\"resourceId\":\"resourceId\",\"permissionTypes\":[\"CREATE\"]}",
            "No resourceType provided.",
            "Bad Request"),
        Arguments.of(
            "{\"ownerId\":\"ownerId\",\"ownerType\":\"USER\",\"resourceId\":\"resourceId\",\"resourceType\":\"RESOURCE\"}",
            "No permissionTypes provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationIdBasedRequestContract(
                "ownerId", OwnerTypeEnum.USER, "resourceId", ResourceTypeEnum.RESOURCE, List.of()),
            "No permissionTypes provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationIdBasedRequestContract(
                "ownerId", OwnerTypeEnum.USER, "foo!!", ResourceTypeEnum.RESOURCE, permissions),
            "The provided resourceId contains illegal characters. It must match the pattern '%s'."
                .formatted(SecurityConfiguration.DEFAULT_ID_REGEX),
            "Bad Request"),
        Arguments.of(
            new AuthorizationIdBasedRequestContract(
                "ownerId!!", OwnerTypeEnum.USER, "foo", ResourceTypeEnum.RESOURCE, permissions),
            "The provided ownerId contains illegal characters. It must match the pattern '%s'."
                .formatted(SecurityConfiguration.DEFAULT_ID_REGEX),
            "Bad Request"),
        // AuthorizationPropertyBasedRequest tests
        Arguments.of(
            "{\"ownerType\":\"USER\",\"resourcePropertyName\":\"resourcePropertyName\",\"resourceType\":\"RESOURCE\",\"permissionTypes\":[\"CREATE\"]}",
            "No ownerId provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationPropertyBasedRequestContract(
                "",
                OwnerTypeEnum.USER,
                "resourcePropertyName",
                ResourceTypeEnum.RESOURCE,
                permissions),
            "No ownerId provided.",
            "Bad Request"),
        Arguments.of(
            "{\"ownerId\":\"ownerId\",\"resourcePropertyName\":\"resourcePropertyName\",\"resourceType\":\"RESOURCE\",\"permissionTypes\":[\"CREATE\"]}",
            "No ownerType provided.",
            "Bad Request"),
        Arguments.of(
            "{\"ownerId\":\"ownerId\",\"ownerType\":\"USER\",\"resourcePropertyName\":\"resourcePropertyName\",\"permissionTypes\":[\"CREATE\"]}",
            "No resourceType provided.",
            "Bad Request"),
        Arguments.of(
            "{\"ownerId\":\"ownerId\",\"ownerType\":\"USER\",\"resourcePropertyName\":\"resourcePropertyName\",\"resourceType\":\"RESOURCE\"}",
            "No permissionTypes provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationPropertyBasedRequestContract(
                "ownerId",
                OwnerTypeEnum.USER,
                "resourcePropertyName",
                ResourceTypeEnum.RESOURCE,
                List.of()),
            "No permissionTypes provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationPropertyBasedRequestContract(
                "ownerId!!",
                OwnerTypeEnum.USER,
                "resourcePropertyName",
                ResourceTypeEnum.RESOURCE,
                permissions),
            "The provided ownerId contains illegal characters. It must match the pattern '%s'."
                .formatted(SecurityConfiguration.DEFAULT_ID_REGEX),
            "Bad Request"),
        Arguments.of(
            new AuthorizationPropertyBasedRequestContract(
                "ownerId", OwnerTypeEnum.USER, "", ResourceTypeEnum.RESOURCE, permissions),
            "Either resourceId or resourcePropertyName must be provided.",
            "Bad Request"),
        Arguments.of(
            new AuthorizationPropertyBasedRequestContract(
                "ownerId",
                OwnerTypeEnum.USER,
                "property!!",
                ResourceTypeEnum.RESOURCE,
                permissions),
            "The provided resourcePropertyName contains illegal characters. It must match the pattern '%s'."
                .formatted(SecurityConfiguration.DEFAULT_ID_REGEX),
            "Bad Request"));
  }
}

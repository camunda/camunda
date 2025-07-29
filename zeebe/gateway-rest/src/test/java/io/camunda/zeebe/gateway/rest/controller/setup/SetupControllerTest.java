/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.setup;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(SetupController.class)
class SetupControllerTest extends RestControllerTest {
  private static final String BASE_PATH = "/v2/setup";
  private static final String USER_PATH = BASE_PATH + "/user";
  @MockitoBean private UserServices userServices;
  @MockitoBean private RoleServices roleServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  private SecurityConfiguration securityConfiguration;

  @BeforeEach
  void setup() {
    final var anonymousAuthentication = CamundaAuthentication.anonymous();
    when(authenticationProvider.getAnonymousCamundaAuthentication())
        .thenReturn(anonymousAuthentication);
    when(userServices.withAuthentication(anonymousAuthentication)).thenReturn(userServices);
    when(roleServices.withAuthentication(anonymousAuthentication)).thenReturn(roleServices);
    when(securityConfiguration.getAuthentication().getMethod())
        .thenReturn(AuthenticationMethod.BASIC);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "Foo", "foo@bar.baz", "f_oo@bar.baz", "foo123", "foo-"})
  void createAdminUserShouldReturnCreated(final String username) {
    final var dto = validCreateUserRequest(username);
    final var userRecord =
        new UserRecord()
            .setUsername(dto.username())
            .setName(dto.name())
            .setEmail(dto.email())
            .setPassword(dto.password());
    whenNoAdminUserExists();
    when(userServices.createInitialAdminUser(dto))
        .thenReturn(CompletableFuture.completedFuture(userRecord));

    // when
    webClient
        .post()
        .uri(USER_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
          {
            "username": "%s",
            "name": "%s",
            "email": "%s"
          }
        """
                .formatted(dto.username(), dto.name(), dto.email()),
            JsonCompareMode.STRICT);

    // then
    verify(userServices, times(1)).createInitialAdminUser(dto);
  }

  @Test
  void createAdminUserShouldReturnForbiddenWhenAuthenticationIsNotBasicAuth() {
    final var dto = validCreateUserRequest(UUID.randomUUID().toString());
    when(securityConfiguration.getAuthentication().getMethod())
        .thenReturn(AuthenticationMethod.OIDC);

    // when then
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, SetupController.WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE);
    expectedBody.setTitle("FORBIDDEN");
    expectedBody.setInstance(URI.create(USER_PATH));

    webClient
        .post()
        .uri(USER_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void createAdminUserShouldReturnForbiddenWhenAdminUserExists() {
    final var dto = validCreateUserRequest(UUID.randomUUID().toString());
    whenAdminUserExists();

    // when then
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, SetupController.ADMIN_EXISTS_ERROR_MESSAGE);
    expectedBody.setTitle("FORBIDDEN");
    expectedBody.setInstance(URI.create(USER_PATH));

    webClient
        .post()
        .uri(USER_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void shouldRejectUserCreationWithMissingUsername() {
    // given
    final var request = validUserWithPasswordRequest().username(null);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No username provided.",
              "instance": "%s"
            }"""
            .formatted(USER_PATH));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithBlankUsername() {
    // given
    final var request = validUserWithPasswordRequest().username("");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No username provided.",
              "instance": "%s"
            }"""
            .formatted(USER_PATH));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithEmptyPassword() {
    // given
    final var request = validUserWithPasswordRequest().password(null);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No password provided.",
              "instance": "%s"
            }"""
            .formatted(USER_PATH));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithBlankPassword() {
    // given
    final var request = validUserWithPasswordRequest().password("");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No password provided.",
              "instance": "%s"
            }"""
            .formatted(USER_PATH));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldCreateUserWithEmptyNameAndEmail() {
    // given
    final var dto = new UserDTO("foo", null, null, "zabraboof");
    final var userRecord = new UserRecord().setUsername(dto.username()).setPassword(dto.password());
    whenNoAdminUserExists();
    when(userServices.createInitialAdminUser(dto))
        .thenReturn(CompletableFuture.completedFuture(userRecord));

    // when
    webClient
        .post()
        .uri(USER_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
          {
            "username": "%s",
            "name": "",
            "email": ""
          }
        """
                .formatted(dto.username()),
            JsonCompareMode.STRICT);

    // then
    verify(userServices, times(1)).createInitialAdminUser(dto);
  }

  @Test
  void shouldRejectUserCreationWithInvalidEmail() {
    // given
    final var email = "invalid@email.reject";
    final var request = validUserWithPasswordRequest().email(email);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided email '%s' is not valid.",
              "instance": "%s"
            }"""
            .formatted(email, USER_PATH));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithTooLongUsername() {
    // given
    final var username = "x".repeat(257);
    final var request = validUserWithPasswordRequest().username(username);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided username exceeds the limit of 256 characters.",
              "instance": "%s"
            }"""
            .formatted(USER_PATH));
    verifyNoInteractions(userServices);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "foo~", "foo!", "foo#", "foo$", "foo%", "foo^", "foo&", "foo*", "foo(", "foo)", "foo=",
        "foo+", "foo{", "foo[", "foo}", "foo]", "foo|", "foo\\", "foo:", "foo;", "foo\"", "foo'",
        "foo<", "foo>", "foo,", "foo?", "foo/", "foo ", "foo\t", "foo\n", "foo\r"
      })
  void shouldRejectUserCreationWithIllegalCharactersInUsername(final String username) {
    // given
    final var request = validUserWithPasswordRequest().username(username);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided username contains illegal characters. It must match the pattern '%s'.",
              "instance": "%s"
            }"""
            .formatted(IdentifierPatterns.ID_PATTERN, USER_PATH));
    verifyNoInteractions(userServices);
  }

  private void whenNoAdminUserExists() {
    when(roleServices.hasMembersOfType(DefaultRole.ADMIN.getId(), EntityType.USER))
        .thenReturn(false);
  }

  private void whenAdminUserExists() {
    when(roleServices.hasMembersOfType(DefaultRole.ADMIN.getId(), EntityType.USER))
        .thenReturn(true);
  }

  private UserDTO validCreateUserRequest(final String username) {
    return new UserDTO(username, "Foo Bar", "bar@example.com", "zabraboof");
  }

  private UserRequest validUserWithPasswordRequest() {
    return new UserRequest()
        .username("foo")
        .name("Foo Bar")
        .email("bar@baz.com")
        .password("zabraboof");
  }

  private void assertRequestRejectedExceptionally(
      final UserRequest request, final String expectedError) {
    webClient
        .post()
        .uri(USER_PATH)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedError, JsonCompareMode.STRICT);
  }
}

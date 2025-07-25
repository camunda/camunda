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

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.validator.IdentifierPatterns;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(UserController.class)
public class UserControllerTest extends RestControllerTest {

  private static final String USER_BASE_URL = "/v2/users";

  @MockitoBean private UserServices userServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(userServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userServices);
  }

  @ParameterizedTest
  @ValueSource(strings = {"foo", "Foo", "foo@bar.baz", "f_oo@bar.baz", "foo123", "foo-"})
  void createUserShouldReturnCreated(final String username) {
    // given
    final var dto = validCreateUserRequest(username);

    final var userRecord =
        new UserRecord()
            .setUsername(dto.username())
            .setName(dto.name())
            .setEmail(dto.email())
            .setPassword(dto.password());

    when(userServices.createUser(dto)).thenReturn(CompletableFuture.completedFuture(userRecord));

    // when
    webClient
        .post()
        .uri(USER_BASE_URL)
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
            "name": "Foo Bar",
            "email": "bar@baz.com"
          }
        """
                .formatted(username),
            JsonCompareMode.STRICT);

    // then
    verify(userServices, times(1)).createUser(dto);
  }

  @Test
  void createUserThrowsExceptionWhenServiceThrowsException() {
    // given
    final String message = "message";

    final var dto = validCreateUserRequest("foo");

    when(userServices.createUser(dto))
        .thenThrow(
            ErrorMapper.mapBrokerRejection(
                new BrokerRejection(UserIntent.CREATE, -1, RejectionType.ALREADY_EXISTS, message)));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
    expectedBody.setTitle(RejectionType.ALREADY_EXISTS.name());
    expectedBody.setDetail("Command 'CREATE' rejected with code 'ALREADY_EXISTS': " + message);
    expectedBody.setInstance(URI.create(USER_BASE_URL));

    // when then
    webClient
        .post()
        .uri(USER_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT.value())
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
            .formatted(USER_BASE_URL));
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
            .formatted(USER_BASE_URL));
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
            .formatted(USER_BASE_URL));
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
            .formatted(USER_BASE_URL));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldCreateUserWithEmptyNameAndEmail() {
    // given
    final var dto = new UserDTO("foo", null, null, "zabraboof");
    final var userRecord = new UserRecord().setUsername(dto.username()).setPassword(dto.password());
    when(userServices.createUser(dto)).thenReturn(CompletableFuture.completedFuture(userRecord));

    // when
    webClient
        .post()
        .uri(USER_BASE_URL)
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
    verify(userServices, times(1)).createUser(dto);
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
            .formatted(email, USER_BASE_URL));
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
            .formatted(USER_BASE_URL));
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
            .formatted(IdentifierPatterns.ID_PATTERN, USER_BASE_URL));
    verifyNoInteractions(userServices);
  }

  @Test
  void deleteUserShouldReturnNoContent() {
    // given
    final String username = "tester";

    final var userRecord = new UserRecord().setUsername(username);

    when(userServices.deleteUser(username))
        .thenReturn(CompletableFuture.completedFuture(userRecord));

    // when
    webClient
        .delete()
        .uri("%s/%s".formatted(USER_BASE_URL, username))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(userServices, times(1)).deleteUser(username);
  }

  @Test
  void updateUserShouldReturnOk() {
    // given
    final String username = "alice-test";
    final UserDTO user = new UserDTO(username, "Alice", "test+alice@camunda.com", null);
    when(userServices.updateUser(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserRecord()
                    .setName(user.name())
                    .setUsername(user.username())
                    .setEmail(user.email())));

    // when / then
    webClient
        .put()
        .uri("%s/%s".formatted(USER_BASE_URL, user.username()))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            new UserUpdateRequest().name(user.name()).email(user.email()).password(user.password()))
        .exchange()
        .expectStatus()
        .isOk();

    verify(userServices, times(1)).updateUser(user);
  }

  private UserDTO validCreateUserRequest(final String username) {
    return new UserDTO(username, "Foo Bar", "bar@baz.com", "zabraboof");
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
        .uri(USER_BASE_URL)
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

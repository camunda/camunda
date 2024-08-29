/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.service.CamundaServiceException;
import io.camunda.service.UserServices;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.protocol.rest.UserWithPasswordRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.UserController;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;

@WebMvcTest(UserController.class)
public class UserControllerTest extends RestControllerTest {

  private static final String USER_BASE_URL = "/v2/users";

  @MockBean private UserServices userServices;
  @MockBean private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setup() {
    when(userServices.withAuthentication(any(Authentication.class))).thenReturn(userServices);
    when(passwordEncoder.encode(any()))
        .thenAnswer(
            (Answer<String>) invocationOnMock -> invocationOnMock.getArgument(0).toString());
  }

  @Test
  void createUserShouldReturnNoContent() {
    // given
    final UserWithPasswordRequest dto = new UserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setName("Demo");
    dto.setEmail("demo@e.c");

    final var userRecord =
        new UserRecord()
            .setUsername(dto.getUsername())
            .setName(dto.getName())
            .setEmail(dto.getEmail())
            .setPassword(dto.getPassword());

    when(userServices.createUser(
            dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword()))
        .thenReturn(CompletableFuture.completedFuture(userRecord));

    // when
    webClient
        .post()
        .uri(USER_BASE_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isNoContent();

    // then
    verify(userServices, times(1))
        .createUser(dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword());
  }

  @Test
  void createUserThrowsExceptionWhenServiceThrowsException() {
    // given
    final String message = "message";

    final UserWithPasswordRequest dto = new UserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setEmail("demo@e.c");
    dto.setPassword("password");

    when(userServices.createUser(
            dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword()))
        .thenThrow(new CamundaServiceException(RejectionType.ALREADY_EXISTS.name()));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle("Bad Request");
    expectedBody.setDetail(RejectionType.ALREADY_EXISTS.name());
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
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void shouldRejectUserCreationWithMissingUsername() {
    // given
    final var request = validRequest().username(null);

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
    final var request = validRequest().username("");

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
  void shouldRejectUserCreationWithEmptyName() {
    // given
    final var request = validRequest().name(null);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No name provided.",
          "instance": "%s"
        }"""
            .formatted(USER_BASE_URL));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithBlankName() {
    // given
    final var request = validRequest().name("");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No name provided.",
          "instance": "%s"
        }"""
            .formatted(USER_BASE_URL));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithEmptyPassword() {
    // given
    final var request = validRequest().password(null);

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
    final var request = validRequest().password("");

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
  void shouldRejectUserCreationWithEmptyEmail() {
    // given
    final var request = validRequest().email(null);

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No email provided.",
          "instance": "%s"
        }"""
            .formatted(USER_BASE_URL));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithBlankEmail() {
    // given
    final var request = validRequest().email("");

    // when then
    assertRequestRejectedExceptionally(
        request,
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No email provided.",
          "instance": "%s"
        }"""
            .formatted(USER_BASE_URL));
    verifyNoInteractions(userServices);
  }

  @Test
  void shouldRejectUserCreationWithInvalidEmail() {
    // given
    final var email = "invalid@email.reject";
    final var request = validRequest().email(email);

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

  private UserWithPasswordRequest validRequest() {
    return new UserWithPasswordRequest()
        .username("foo")
        .name("Foo Bar")
        .email("bar@baz.com")
        .password("zabraboof");
  }

  private void assertRequestRejectedExceptionally(
      final UserWithPasswordRequest request, final String expectedError) {
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
        .json(expectedError);
  }
}

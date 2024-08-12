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

  @MockBean private UserServices<UserRecord> userServices;
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

    webClient
        .post()
        .uri("/v2/users")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(userServices, times(1))
        .createUser(dto.getUsername(), dto.getName(), dto.getEmail(), dto.getPassword());
  }

  @Test
  void createUserThrowsExceptionWhenServiceThrowsException() {
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
    expectedBody.setInstance(URI.create("/v2/users"));

    webClient
        .post()
        .uri("/v2/users")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }
}

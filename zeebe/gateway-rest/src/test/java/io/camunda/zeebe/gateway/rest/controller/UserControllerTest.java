/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResponse;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.UserController;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

@WebMvcTest(UserController.class)
public class UserControllerTest extends RestControllerTest {

  @MockBean private UserService userService;

  @Test
  void getUserByIdShouldReturnExistingUser() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);

    when(userService.findUserById(1L)).thenReturn(camundaUser);

    webClient
        .get()
        .uri("/v2/users/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaUserResponse.class)
        .isEqualTo(camundaUserDto);
  }

  @Test
  void getUserByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";
    when(userService.findUserById(1L)).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/users/1"));

    webClient
        .get()
        .uri("/v2/users/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void createUserShouldCreateAndReturnNewUser() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setEnabled(true);

    when(userService.createUser(camundaUserWithPassword)).thenReturn(camundaUser);

    webClient
        .post()
        .uri("/v2/users")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaUserResponse.class)
        .isEqualTo(camundaUserDto);

    verify(userService, times(1)).createUser(camundaUserWithPassword);
  }

  @Test
  void createUserByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setEnabled(true);

    when(userService.createUser(camundaUserWithPassword))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
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

  @Test
  void updateUserShouldUpdateAndReturnUpdatedUser() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");
    camundaUser.setName("updatedName");

    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);
    camundaUserDto.setName("updatedName");

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setName("updatedName");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setName("updatedName");
    dto.setEnabled(true);

    when(userService.updateUser(1L, camundaUserWithPassword)).thenReturn(camundaUser);

    webClient
        .put()
        .uri("/v2/users/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaUserResponse.class)
        .isEqualTo(camundaUserDto);

    verify(userService, times(1)).updateUser(1L, camundaUserWithPassword);
  }

  @Test
  void updateUserByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordRequest dto = new CamundaUserWithPasswordRequest();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setEnabled(true);

    when(userService.updateUser(1L, camundaUserWithPassword))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create("/v2/users/1"));

    webClient
        .put()
        .uri("/v2/users/1")
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
  void deleteUserShouldRemoveExistingUser() {

    webClient
        .delete()
        .uri("/v2/users/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful();

    verify(userService, times(1)).deleteUser(1L);
  }

  @Test
  void searchUsersShouldReturnAllUsers() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);

    final UserSearchResponse userSearchResponseDto = new UserSearchResponse();
    userSearchResponseDto.setItems(List.of(camundaUserDto));

    final SearchQueryRequest searchQueryRequest = new SearchQueryRequest();

    when(userService.findAllUsers()).thenReturn(List.of(camundaUser));

    webClient
        .post()
        .uri("/v2/users/search")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchQueryRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(UserSearchResponse.class)
        .isEqualTo(userSearchResponseDto);

    verify(userService, times(1)).findAllUsers();
  }

  @Test
  void searchUsersWithoutRequestBodyShouldReturnAllUsers() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);

    final UserSearchResponse userSearchResponseDto = new UserSearchResponse();
    userSearchResponseDto.setItems(List.of(camundaUserDto));

    when(userService.findAllUsers()).thenReturn(List.of(camundaUser));

    webClient
        .post()
        .uri("/v2/users/search")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(UserSearchResponse.class)
        .isEqualTo(userSearchResponseDto);

    verify(userService, times(1)).findAllUsers();
  }
}

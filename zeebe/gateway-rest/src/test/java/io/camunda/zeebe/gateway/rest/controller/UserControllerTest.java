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
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserDto;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordDto;
import io.camunda.zeebe.gateway.protocol.rest.SearchRequestDto;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponseDto;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.UserController;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebMvcTest(
    controllers = {UserController.class},
    excludeAutoConfiguration = {
      SecurityAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      DataSourceAutoConfiguration.class
    })
public class UserControllerTest {

  @MockBean private UserService userService;

  @Autowired private WebTestClient webClient;

  @Test
  void getUserByIdWorks() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserDto camundaUserDto = new CamundaUserDto();
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
        .expectBody(CamundaUserDto.class)
        .isEqualTo(camundaUserDto);
  }

  @Test
  void getUserByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";
    when(userService.findUserById(1L)).thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
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
  void createUserWorks() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserDto camundaUserDto = new CamundaUserDto();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordDto dto = new CamundaUserWithPasswordDto();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setEnabled(true);

    when(userService.createUserFailIfExists(camundaUserWithPassword)).thenReturn(camundaUser);

    webClient
        .post()
        .uri("/v2/users")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(dto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(CamundaUserDto.class)
        .isEqualTo(camundaUserDto);

    verify(userService, times(1)).createUserFailIfExists(camundaUserWithPassword);
  }

  @Test
  void createUserByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordDto dto = new CamundaUserWithPasswordDto();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setEnabled(true);

    when(userService.createUserFailIfExists(camundaUserWithPassword))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
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
  void updateUserWorks() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");
    camundaUser.setName("updatedName");

    final CamundaUserDto camundaUserDto = new CamundaUserDto();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);
    camundaUserDto.setName("updatedName");

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setName("updatedName");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordDto dto = new CamundaUserWithPasswordDto();
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
        .expectBody(CamundaUserDto.class)
        .isEqualTo(camundaUserDto);

    verify(userService, times(1)).updateUser(1L, camundaUserWithPassword);
  }

  @Test
  void updateUserByIdThrowsExceptionWhenServiceThrowsException() {
    final String message = "message";

    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();
    camundaUserWithPassword.setUsername("demo");
    camundaUserWithPassword.setPassword("password");

    final CamundaUserWithPasswordDto dto = new CamundaUserWithPasswordDto();
    dto.setUsername("demo");
    dto.setPassword("password");
    dto.setEnabled(true);

    when(userService.updateUser(1L, camundaUserWithPassword))
        .thenThrow(new IllegalArgumentException(message));

    final var expectedBody = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
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
  void deleteUserWorks() {

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
  void searchUsersWorks() {
    final CamundaUser camundaUser = new CamundaUser();
    camundaUser.setUsername("demo");

    final CamundaUserDto camundaUserDto = new CamundaUserDto();
    camundaUserDto.setUsername("demo");
    camundaUserDto.setEnabled(true);

    final UserSearchResponseDto userSearchResponseDto = new UserSearchResponseDto();
    userSearchResponseDto.setItems(List.of(camundaUserDto));

    final SearchRequestDto searchRequestDto = new SearchRequestDto();

    when(userService.findAllUsers()).thenReturn(List.of(camundaUser));

    webClient
        .post()
        .uri("/v2/users/search")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(searchRequestDto)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(UserSearchResponseDto.class)
        .isEqualTo(userSearchResponseDto);

    verify(userService, times(1)).findAllUsers();
  }
}

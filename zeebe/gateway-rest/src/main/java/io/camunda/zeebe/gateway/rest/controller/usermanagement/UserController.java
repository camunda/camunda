/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResponse;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ZeebeRestController
@RequestMapping("/v2/users")
public class UserController {
  private final UserService userService;

  public UserController(final UserService userService) {
    this.userService = userService;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createUser(
      @RequestBody final CamundaUserWithPasswordRequest userWithPasswordDto,
      final HttpServletRequest request) {
    try {
      final CamundaUserResponse camundaUserResponse =
          mapToCamundaUserResponse(
              userService.createUserFailIfExists(mapToUserWithPassword(userWithPasswordDto)));
      return new ResponseEntity<>(camundaUserResponse, HttpStatus.CREATED);
    } catch (final Exception e) {
      return handleException(e, request.getRequestURI());
    }
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Object> deleteUser(
      @PathVariable final Long id, final HttpServletRequest request) {
    try {
      userService.deleteUser(id);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } catch (final Exception e) {
      return handleException(e, request.getRequestURI());
    }
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> findUserById(
      @PathVariable final Long id, final HttpServletRequest request) {
    try {
      final CamundaUserResponse camundaUserResponse =
          mapToCamundaUserResponse(userService.findUserById(id));
      return new ResponseEntity<>(camundaUserResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return handleException(e, request.getRequestURI());
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllUsers(
      @RequestBody final SearchQueryRequest searchQueryRequest, final HttpServletRequest request) {
    try {
      final UserSearchResponse responseDto = new UserSearchResponse();
      final List<CamundaUserResponse> allUsers =
          userService.findAllUsers().stream().map(this::mapToCamundaUserResponse).toList();
      responseDto.setItems(allUsers);

      return new ResponseEntity<>(responseDto, HttpStatus.OK);
    } catch (final Exception e) {
      return handleException(e, request.getRequestURI());
    }
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> updateUser(
      @PathVariable final Long id,
      @RequestBody final CamundaUserWithPasswordRequest user,
      final HttpServletRequest request) {
    try {
      final CamundaUserResponse camundaUserResponse =
          mapToCamundaUserResponse(userService.updateUser(id, mapToUserWithPassword(user)));
      return new ResponseEntity<>(camundaUserResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return handleException(e, request.getRequestURI());
    }
  }

  private CamundaUserWithPassword mapToUserWithPassword(final CamundaUserWithPasswordRequest dto) {
    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();

    camundaUserWithPassword.setId(dto.getId());
    camundaUserWithPassword.setUsername(dto.getUsername());
    camundaUserWithPassword.setPassword(dto.getPassword());
    camundaUserWithPassword.setName(dto.getName());
    camundaUserWithPassword.setEmail(dto.getEmail());
    camundaUserWithPassword.setEnabled(dto.getEnabled());

    return camundaUserWithPassword;
  }

  private CamundaUserResponse mapToCamundaUserResponse(final CamundaUser camundaUser) {
    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setId(camundaUser.getId());
    camundaUserDto.setUsername(camundaUser.getUsername());
    camundaUserDto.setName(camundaUser.getName());
    camundaUserDto.setEmail(camundaUser.getEmail());
    camundaUserDto.setEnabled(camundaUser.isEnabled());

    return camundaUserDto;
  }

  private ResponseEntity<Object> handleException(final Exception e, final String requestUri) {
    if (e instanceof IllegalArgumentException) {
      final ProblemDetail problemDetail =
          ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
      problemDetail.setInstance(URI.create(requestUri));
      return ResponseEntity.of(problemDetail).build();
    }

    final ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    problemDetail.setInstance(URI.create(requestUri));
    return ResponseEntity.of(problemDetail).build();
  }
}

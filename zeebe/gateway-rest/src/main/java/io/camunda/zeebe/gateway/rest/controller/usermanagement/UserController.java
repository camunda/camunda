/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RequestMapper.getCamundaUserResponse;
import static io.camunda.zeebe.gateway.rest.RequestMapper.getUserWithPassword;

import io.camunda.identity.automation.usermanagement.service.UserService;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResponse;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
      @RequestBody final CamundaUserWithPasswordRequest userWithPasswordDto) {
    try {
      final CamundaUserResponse camundaUserResponse =
          getCamundaUserResponse(userService.createUser(getUserWithPassword(userWithPasswordDto)));
      return new ResponseEntity<>(camundaUserResponse, HttpStatus.CREATED);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Object> deleteUser(@PathVariable final Long id) {
    try {
      userService.deleteUser(id);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> findUserById(@PathVariable final Long id) {
    try {
      final CamundaUserResponse camundaUserResponse =
          getCamundaUserResponse(userService.findUserById(id));
      return new ResponseEntity<>(camundaUserResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllUsers(
      @RequestBody(required = false) final SearchQueryRequest searchQueryRequest) {
    try {
      final UserSearchResponse responseDto = new UserSearchResponse();
      final List<CamundaUserResponse> allUsers =
          userService.findAllUsers().stream().map(RequestMapper::getCamundaUserResponse).toList();
      responseDto.setItems(allUsers);

      return new ResponseEntity<>(responseDto, HttpStatus.OK);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> updateUser(
      @PathVariable final Long id, @RequestBody final CamundaUserWithPasswordRequest user) {
    try {
      final CamundaUserResponse camundaUserResponse =
          getCamundaUserResponse(userService.updateUser(id, getUserWithPassword(user)));
      return new ResponseEntity<>(camundaUserResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.rolemanagement.service.RoleMembershipService;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import io.camunda.zeebe.gateway.protocol.rest.SearchRequestDto;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.dto.RoleAssignRequest;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.dto.SearchResponseDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@ZeebeRestController
@RequestMapping("/v2")
public class UserController {
  private final UserService userService;
  private final RoleMembershipService roleMembershipService;

  public UserController(
      final UserService userService, final RoleMembershipService roleMembershipService) {
    this.userService = userService;
    this.roleMembershipService = roleMembershipService;
  }

  @PostMapping(
      path = "/users",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public CamundaUser createUser(@RequestBody final CamundaUserWithPassword user) {
    return userService.createUser(user);
  }

  @DeleteMapping(path = "/users/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteUser(@PathVariable final Long id) {
    userService.deleteUser(id);
  }

  @GetMapping(
      path = "/users/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CamundaUser findUserByUsername(@PathVariable final Long id) {
    return userService.findUserById(id);
  }

  @PostMapping(
      path = "/users/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponseDto<CamundaUser> findAllUsers(
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<CamundaUser> responseDto = new SearchResponseDto<>();
    final List<CamundaUser> allUsers = userService.findAllUsers();
    responseDto.setItems(allUsers);

    return responseDto;
  }

  @PutMapping(
      path = "/users/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CamundaUser updateUser(
      @PathVariable final Long id, @RequestBody final CamundaUserWithPassword user) {
    return userService.updateUser(id, user);
  }

  @PostMapping(
      path = "/users/{id}/roles/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponseDto<String> findRolesOfUser(
      @PathVariable("id") final Long userId, @RequestBody final SearchRequestDto searchRequestDto) {
    final List<String> roleNames = roleMembershipService.getRolesOfUserByUserId(userId);
    final SearchResponseDto<String> responseDto = new SearchResponseDto<>();
    responseDto.setItems(roleNames);
    return responseDto;
  }

  @PostMapping(path = "/users/{id}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignRoleToUser(
      @PathVariable("id") final long userId, @RequestBody final RoleAssignRequest request) {
    roleMembershipService.assignRoleToUser(request.roleName(), userId);
  }

  @DeleteMapping(path = "/users/{id}/roles/{roleName}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unassignRoleFromUser(
      @PathVariable("id") final long userId, @PathVariable final String roleName) {
    roleMembershipService.unassignRoleFromUser(roleName, userId);
  }
}

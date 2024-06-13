/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.rolemanagement.model.Permission;
import io.camunda.identity.rolemanagement.model.Role;
import io.camunda.identity.rolemanagement.service.RoleService;
import io.camunda.zeebe.gateway.protocol.rest.SearchRequestDto;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import io.camunda.zeebe.gateway.rest.controller.usermanagement.dto.AssignPermissionRequest;
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
public class RoleController {

  private final RoleService roleService;

  public RoleController(final RoleService roleService) {
    this.roleService = roleService;
  }

  @PostMapping(
      path = "/roles",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Role createRole(@RequestBody final Role role) {
    return roleService.createRole(role);
  }

  @DeleteMapping(path = "/roles/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRoleById(@PathVariable("id") final long id) {
    roleService.deleteRoleById(id);
  }

  @GetMapping(
      path = "/roles/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public Role getRoleById(@PathVariable("id") final long id) {
    return roleService.findRoleById(id);
  }

  @PostMapping(
      path = "/roles/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponseDto<Role> findAllRoles(
      @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<Role> roleSearchResponseDto = new SearchResponseDto<>();
    final List<Role> allRoles = roleService.findAllRoles();
    roleSearchResponseDto.setItems(allRoles);

    return roleSearchResponseDto;
  }

  @PutMapping(
      path = "/users/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public Role updateRole(@PathVariable("id") final long id, @RequestBody final Role role) {
    return roleService.updateRole(id, role);
  }

  // list permissions of role
  @PostMapping(
      path = "/roles/{id}/permissions/search}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public SearchResponseDto<Permission> findAllPermissions(
      @PathVariable final long id, @RequestBody final SearchRequestDto searchRequestDto) {
    final SearchResponseDto<Permission> permissionSearchResponseDto = new SearchResponseDto<>();
    final List<Permission> allPermissionsOfRole = roleService.findAllPermissionsOfRole(id);
    permissionSearchResponseDto.setItems(allPermissionsOfRole);

    return permissionSearchResponseDto;
  }

  // assign permission to role
  @PostMapping(path = "/roles/{id}/permissions", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignPermissionsToRole(
      @PathVariable("id") final long roleId, @RequestBody final AssignPermissionRequest request) {
    roleService.assignPermissionToRole(roleId, request.permissionId());
  }

  // delete permission from role
}

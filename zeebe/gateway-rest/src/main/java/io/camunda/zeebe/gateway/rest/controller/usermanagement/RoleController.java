/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.automation.rolemanagement.model.Role;
import io.camunda.identity.automation.rolemanagement.service.RoleService;
import io.camunda.zeebe.gateway.protocol.rest.RoleRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleResponse;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import java.util.HashSet;
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
@RequestMapping("/v2/roles")
public class RoleController {

  private final RoleService roleService;

  public RoleController(final RoleService roleService) {
    this.roleService = roleService;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createRole(@RequestBody final RoleRequest roleRequest) {
    try {
      final RoleResponse roleResponse =
          mapToRoleResponse(roleService.createRole(mapToRole(roleRequest)));
      return new ResponseEntity<>(roleResponse, HttpStatus.CREATED);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @DeleteMapping(path = "/{id}")
  public ResponseEntity<Object> deleteRoleById(@PathVariable("id") final String roleName) {
    try {
      roleService.deleteRoleByName(roleName);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @GetMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getRoleById(@PathVariable("id") final String roleName) {
    try {
      final RoleResponse roleResponse = mapToRoleResponse(roleService.findRoleByName(roleName));
      return new ResponseEntity<>(roleResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> findAllRoles(
      @RequestBody(required = false) final SearchQueryRequest searchQueryRequest) {
    try {
      final RoleSearchResponse roleSearchResponse = new RoleSearchResponse();
      final List<RoleResponse> allRoleResponses =
          roleService.findAllRoles().stream().map(this::mapToRoleResponse).toList();
      roleSearchResponse.setItems(allRoleResponses);

      return new ResponseEntity<>(roleSearchResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  @PutMapping(
      path = "/{id}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> updateRole(
      @PathVariable("id") final String roleName, @RequestBody final RoleRequest roleRequest) {
    try {
      final RoleResponse roleResponse =
          mapToRoleResponse(roleService.updateRole(roleName, mapToRole(roleRequest)));
      return new ResponseEntity<>(roleResponse, HttpStatus.OK);
    } catch (final Exception e) {
      return RestErrorMapper.mapUserManagementExceptionsToResponse(e);
    }
  }

  private Role mapToRole(final RoleRequest roleRequest) {
    final Role role = new Role();
    role.setName(roleRequest.getName());
    role.setDescription(roleRequest.getDescription());
    role.setPermissions(new HashSet<>(roleRequest.getPermissions()));
    return role;
  }

  private RoleResponse mapToRoleResponse(final Role role) {
    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName(role.getName());
    roleResponse.setDescription(role.getDescription());
    roleResponse.setPermissions(role.getPermissions().stream().toList());
    return roleResponse;
  }
}

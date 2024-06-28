/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.permissions.PermissionEnum;
import io.camunda.identity.rolemanagement.model.Role;
import io.camunda.identity.usermanagement.CamundaGroup;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupRequest;
import io.camunda.zeebe.gateway.protocol.rest.CamundaGroupResponse;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResponse;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.protocol.rest.Permission;
import io.camunda.zeebe.gateway.protocol.rest.RoleRequest;
import io.camunda.zeebe.gateway.protocol.rest.RoleResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserManagementMapper {

  public static CamundaUserWithPassword mapToUserWithPassword(
      final CamundaUserWithPasswordRequest dto) {
    final CamundaUserWithPassword camundaUserWithPassword = new CamundaUserWithPassword();

    camundaUserWithPassword.setId(dto.getId());
    camundaUserWithPassword.setUsername(dto.getUsername());
    camundaUserWithPassword.setPassword(dto.getPassword());
    camundaUserWithPassword.setName(dto.getName());
    camundaUserWithPassword.setEmail(dto.getEmail());
    camundaUserWithPassword.setEnabled(dto.getEnabled());

    return camundaUserWithPassword;
  }

  public static CamundaUserResponse mapToCamundaUserResponse(final CamundaUser camundaUser) {
    final CamundaUserResponse camundaUserDto = new CamundaUserResponse();
    camundaUserDto.setId(camundaUser.getId());
    camundaUserDto.setUsername(camundaUser.getUsername());
    camundaUserDto.setName(camundaUser.getName());
    camundaUserDto.setEmail(camundaUser.getEmail());
    camundaUserDto.setEnabled(camundaUser.isEnabled());

    return camundaUserDto;
  }

  public static CamundaGroup mapToGroup(final CamundaGroupRequest groupRequest) {
    return new CamundaGroup(groupRequest.getId(), groupRequest.getName());
  }

  public static CamundaGroupResponse mapToGroupResponse(final CamundaGroup group) {
    final CamundaGroupResponse camundaGroupResponse = new CamundaGroupResponse();
    camundaGroupResponse.setId(group.id());
    camundaGroupResponse.setName(group.name());
    return camundaGroupResponse;
  }

  public static Role mapToRole(final RoleRequest roleRequest) {
    final Role role = new Role();
    role.setName(roleRequest.getName());
    role.setDescription(roleRequest.getDescription());
    role.setPermissions(mapPermissionsToEnumsSet(roleRequest.getPermissions()));
    return role;
  }

  public static Set<PermissionEnum> mapPermissionsToEnumsSet(final List<Permission> permissions) {
    return permissions.stream()
        .map(UserManagementMapper::mapToPermissionEnum)
        .collect(Collectors.toSet());
  }

  public static PermissionEnum mapToPermissionEnum(final Permission permission) {
    return switch (permission) {
      case READ_ALL -> PermissionEnum.READ_ALL;
      case CREATE_ALL -> PermissionEnum.CREATE_ALL;
      case DELETE_ALL -> PermissionEnum.DELETE_ALL;
      case UPDATE_ALL -> PermissionEnum.UPDATE_ALL;
      default -> throw new IllegalArgumentException("Unsupported permission: " + permission);
    };
  }

  public static RoleResponse mapToRoleResponse(final Role role) {
    final RoleResponse roleResponse = new RoleResponse();
    roleResponse.setName(role.getName());
    roleResponse.setDescription(role.getDescription());
    roleResponse.setPermissions(mapPermissionEnumsToPermissions(role.getPermissions()));
    return roleResponse;
  }

  public static List<Permission> mapPermissionEnumsToPermissions(
      final Set<PermissionEnum> permissionEnums) {
    return permissionEnums.stream().map(UserManagementMapper::mapToPermission).toList();
  }

  public static Permission mapToPermission(final PermissionEnum permissionEnum) {
    return switch (permissionEnum) {
      case READ_ALL -> Permission.READ_ALL;
      case CREATE_ALL -> Permission.CREATE_ALL;
      case DELETE_ALL -> Permission.DELETE_ALL;
      case UPDATE_ALL -> Permission.UPDATE_ALL;
      default ->
          throw new IllegalArgumentException("Unsupported permissionEnum: " + permissionEnum);
    };
  }
}

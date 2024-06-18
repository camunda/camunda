/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.service;

import io.camunda.identity.permissions.PermissionEnum;
import io.camunda.identity.rolemanagement.model.Role;
import io.camunda.identity.rolemanagement.repository.RoleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class RoleService {
  private final RoleRepository roleRepository;

  public RoleService(final RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  public Role createRole(final Role role) {
    if (!StringUtils.hasText(role.getName())) {
      throw new RuntimeException("role.notValid");
    }

    if (role.getPermissions() == null) {
      role.setPermissions(new HashSet<>());
    }

    return roleRepository.save(role);
  }

  public void deleteRoleByName(final String roleName) {
    roleRepository.deleteById(roleName);
  }

  public Role findRoleByName(final String roleName) {
    return roleRepository
        .findById(roleName)
        .orElseThrow(() -> new RuntimeException("role.notFound"));
  }

  public List<Role> findAllRoles() {
    return roleRepository.findAll();
  }

  public Role updateRole(final String roleName, final Role role) {
    if (!Objects.equals(roleName, role.getName())) {
      throw new RuntimeException("role.notFound");
    }

    if (role.getPermissions() == null) {
      role.setPermissions(new HashSet<>());
    }

    final Role existingRole = findRoleByName(roleName);

    existingRole.setDescription(role.getDescription());
    existingRole.setPermissions(role.getPermissions());

    return roleRepository.save(existingRole);
  }

  public List<PermissionEnum> findAllPermissionsOfRole(final String roleName) {
    final Role role = findRoleByName(roleName);
    return role.getPermissions().stream().toList();
  }

  public void assignPermissionToRole(final String roleName, final String permissionName) {
    final PermissionEnum permission = PermissionEnum.valueOf(permissionName);
    final Role role = findRoleByName(roleName);

    role.getPermissions().add(permission);
    roleRepository.save(role);
  }

  public void unassignPermissionFromRole(final String roleName, final String permissionName) {
    final PermissionEnum permission = PermissionEnum.valueOf(permissionName);
    final Role role = findRoleByName(roleName);
    role.getPermissions().remove(permission);
    roleRepository.save(role);
  }
}

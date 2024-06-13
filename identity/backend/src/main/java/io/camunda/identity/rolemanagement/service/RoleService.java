/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.service;

import io.camunda.identity.rolemanagement.model.Permission;
import io.camunda.identity.rolemanagement.model.Role;
import io.camunda.identity.rolemanagement.repository.PermissionRepository;
import io.camunda.identity.rolemanagement.repository.RoleRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleService {
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;

  public RoleService(
      final RoleRepository roleRepository, final PermissionRepository permissionRepository) {
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
  }

  public Role createRole(final Role role) {
    if (role.getId() != null) {
      role.setId(null);
    }
    return roleRepository.save(role);
  }

  public void deleteRoleById(final long roleId) {
    roleRepository.deleteById(roleId);
  }

  public Role findRoleById(final long roleId) {
    return roleRepository.findById(roleId).orElseThrow(() -> new RuntimeException("role.notFound"));
  }

  public List<Role> findAllRoles() {
    return roleRepository.findAll();
  }

  public Role updateRole(final long roleId, final Role role) {
    if (!Objects.equals(roleId, role.getId())) {
      throw new RuntimeException("role.notFound");
    }

    final Role existingRole = findRoleById(roleId);

    existingRole.setName(role.getName());
    existingRole.setDescription(role.getDescription());
    existingRole.setPermissions(role.getPermissions());

    return roleRepository.save(existingRole);
  }

  public List<Permission> findAllPermissionsOfRole(final long roleId) {
    final Role role = findRoleById(roleId);
    return role.getPermissions().stream().toList();
  }

  public void assignPermissionToRole(final long roleId, final long permissionId) {
    final Permission permission =
        permissionRepository
            .findById(permissionId)
            .orElseThrow(() -> new RuntimeException("permission.notFound"));
    final Role role = findRoleById(roleId);

    role.getPermissions().add(permission);
    roleRepository.save(role);
  }
}

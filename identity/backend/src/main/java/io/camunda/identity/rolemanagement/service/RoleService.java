/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.rolemanagement.service;

import io.camunda.identity.rolemanagement.model.Role;
import io.camunda.identity.rolemanagement.repository.RoleRepository;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class RoleService {
  private final RoleRepository roleRepository;

  public RoleService(final RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  public Role createRole(@Valid final Role role) {
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

  public Role updateRole(final String roleName, @Valid final Role role) {
    if (!Objects.equals(roleName, role.getName())) {
      throw new RuntimeException("role.notFound");
    }

    final Role existingRole = findRoleByName(roleName);

    existingRole.setDescription(role.getDescription());
    existingRole.setPermissions(role.getPermissions());

    return roleRepository.save(existingRole);
  }
}

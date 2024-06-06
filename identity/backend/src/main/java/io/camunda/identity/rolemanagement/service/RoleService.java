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
import org.springframework.stereotype.Service;

@Service
public class RoleService {
  private final RoleRepository roleRepository;

  public RoleService(final RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  public Role createRole(final Role role) {
    return roleRepository.save(role);
  }
}

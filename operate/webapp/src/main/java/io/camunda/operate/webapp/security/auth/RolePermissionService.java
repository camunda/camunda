/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.auth;

import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;

import io.camunda.operate.webapp.security.Permission;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionService {

  private final Map<Role, List<Permission>> roles2permissions = new EnumMap<>(Role.class);

  @PostConstruct
  public void init() {
    roles2permissions.put(Role.USER, List.of(READ));
    roles2permissions.put(Role.OPERATOR, List.of(READ, WRITE));
    roles2permissions.put(Role.OWNER, List.of(READ, WRITE));
  }

  public List<Permission> getPermissions(final List<Role> roles) {
    return roles.stream()
        .map(this::getPermissionsForRole)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private List<Permission> getPermissionsForRole(final Role role) {
    return roles2permissions.get(role);
  }
}

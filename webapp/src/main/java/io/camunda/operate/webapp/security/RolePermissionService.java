/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security;

import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;
import static io.camunda.operate.webapp.security.Role.OWNER;
import static io.camunda.operate.webapp.security.Role.USER;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionService {

  private final Map<Role, List<Permission>> roles2permissions = new EnumMap<>(Role.class);

  @PostConstruct
  public void init(){
    roles2permissions.put(USER, List.of(READ));
    roles2permissions.put(OWNER, List.of(READ, WRITE));
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

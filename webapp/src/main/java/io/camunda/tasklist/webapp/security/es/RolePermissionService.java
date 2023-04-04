/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.security.es;

import static io.camunda.tasklist.webapp.security.Permission.READ;
import static io.camunda.tasklist.webapp.security.Permission.WRITE;
import static io.camunda.tasklist.webapp.security.es.Role.OPERATOR;
import static io.camunda.tasklist.webapp.security.es.Role.OWNER;
import static io.camunda.tasklist.webapp.security.es.Role.READER;

import io.camunda.tasklist.webapp.security.Permission;
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
    roles2permissions.put(READER, List.of(READ));
    roles2permissions.put(OPERATOR, List.of(READ, WRITE));
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

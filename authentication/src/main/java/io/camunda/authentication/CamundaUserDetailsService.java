/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder.aCamundaUser;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsService implements UserDetailsService {

  private final UserServices userServices;
  private final AuthorizationServices authorizationServices;
  private final RoleServices roleServices;
  private final TenantServices tenantServices;
  private final GroupServices groupServices;

  public CamundaUserDetailsService(
      final UserServices userServices,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices,
      final TenantServices tenantServices,
      final GroupServices groupServices) {
    this.userServices = userServices;
    this.authorizationServices = authorizationServices;
    this.roleServices = roleServices;
    this.tenantServices = tenantServices;
    this.groupServices = groupServices;
  }

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    final var userQuery =
        SearchQueryBuilders.userSearchQuery(
            fn -> fn.filter(f -> f.usernames(username)).page(p -> p.size(1)));
    final var storedUser =
        userServices.search(userQuery).items().stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException(username));

    final Long userKey = storedUser.userKey();

    final var groups =
        groupServices.getGroupsByMemberId(username, EntityType.USER).stream()
            .map(GroupEntity::groupId)
            .collect(Collectors.toSet());

    final var roles = roleServices.getRolesByUserAndGroups(username, groups);
    final var roleIds = roles.stream().map(RoleEntity::roleId).collect(Collectors.toSet());

    final var authorizedComponents =
        authorizationServices.getAuthorizedComponents(
            Stream.concat(roles.stream().map(RoleEntity::roleId), Stream.of(storedUser.username()))
                .collect(Collectors.toSet()));

    final var tenants =
        tenantServices.getTenantsByUserAndGroupsAndRoles(username, groups, roleIds).stream()
            .map(TenantDTO::fromEntity)
            .toList();

    return aCamundaUser()
        .withUserKey(userKey)
        .withName(storedUser.name())
        .withUsername(storedUser.username())
        .withPassword(storedUser.password())
        .withEmail(storedUser.email())
        .withAuthorizedComponents(authorizedComponents)
        .withRoles(roles)
        .withTenants(tenants)
        .withGroups(groups.stream().toList())
        .withCanLogout(true)
        .build();
  }
}

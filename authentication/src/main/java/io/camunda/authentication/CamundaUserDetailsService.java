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
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsService implements UserDetailsService {

  private final UserServices userServices;
  private final RoleServices roleServices;
  private final TenantServices tenantServices;
  private final GroupServices groupServices;

  public CamundaUserDetailsService(
      final UserServices userServices,
      final RoleServices roleServices,
      final TenantServices tenantServices,
      final GroupServices groupServices) {
    this.userServices = userServices;
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
        userServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .search(userQuery)
            .items()
            .stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException(username));

    final Long userKey = storedUser.userKey();

    final var groups =
        groupServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getGroupsByMemberId(username, EntityType.USER)
            .stream()
            .map(GroupEntity::groupId)
            .collect(Collectors.toSet());

    final var roles =
        roleServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getRolesByUserAndGroups(username, groups)
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    final var tenants =
        tenantServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getTenantsByUserAndGroupsAndRoles(username, groups, roles)
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return aCamundaUser()
        .withUserKey(userKey)
        .withName(storedUser.name())
        .withUsername(storedUser.username())
        .withPassword(storedUser.password())
        .withEmail(storedUser.email())
        .withRoles(roles.stream().toList())
        .withTenants(tenants)
        .withGroups(groups.stream().toList())
        .withCanLogout(true)
        .build();
  }
}

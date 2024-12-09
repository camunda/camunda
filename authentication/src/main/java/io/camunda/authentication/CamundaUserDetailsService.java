/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder.aCamundaUser;

import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.entity.Permission;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsService implements UserDetailsService {

  private final UserServices userServices;
  private final AuthorizationServices authorizationServices;
  private final RoleServices roleServices;

  public CamundaUserDetailsService(
      final UserServices userServices,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices) {
    this.userServices = userServices;
    this.authorizationServices = authorizationServices;
    this.roleServices = roleServices;
  }

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    final var userQuery =
        SearchQueryBuilders.userSearchQuery(
            fn -> fn.filter(f -> f.username(username)).page(p -> p.size(1)));
    final var storedUser =
        userServices.search(userQuery).items().stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new UsernameNotFoundException(username));

    final Long userKey = storedUser.userKey();
    final var authorizationQuery =
        SearchQueryBuilders.authorizationSearchQuery(
            fn ->
                fn.filter(
                    f ->
                        f.ownerKeys(userKey)
                            .permissionType(PermissionType.ACCESS)
                            .resourceType(AuthorizationResourceType.APPLICATION.name())));

    final var authorizedApplications =
        authorizationServices.findAll(authorizationQuery).stream()
            .map(AuthorizationEntity::permissions)
            .flatMap(List::stream)
            .map(Permission::resourceIds)
            .flatMap(Set::stream)
            .collect(Collectors.toList());

    final var roles = roleServices.findAll(RoleQuery.of(q -> q.filter(f -> f.memberKey(userKey))));

    return aCamundaUser()
        .withUserKey(userKey)
        .withName(storedUser.name())
        .withUsername(storedUser.username())
        .withPassword(storedUser.password())
        .withAuthorizedApplications(authorizedApplications)
        .withRoles(roles)
        .build();
  }
}

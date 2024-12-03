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
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.entity.Permission;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
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
  private final TenantServices tenantServices;

  public CamundaUserDetailsService(
      final UserServices userServices,
      final AuthorizationServices authorizationServices,
      final TenantServices tenantServices) {
    this.userServices = userServices;
    this.authorizationServices = authorizationServices;
    this.tenantServices = tenantServices;
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

    final var authorizationQuery =
        SearchQueryBuilders.authorizationSearchQuery(
            fn ->
                fn.filter(
                    f ->
                        f.ownerKeys(storedUser.userKey())
                            .permissionType(PermissionType.ACCESS)
                            .resourceType(AuthorizationResourceType.APPLICATION.name())));

    final var authorizedApplications =
        authorizationServices.search(authorizationQuery).items().stream()
            .map(AuthorizationEntity::permissions)
            .flatMap(List::stream)
            .map(Permission::resourceIds)
            .flatMap(Set::stream)
            .collect(Collectors.toList());

    final var tenants =
        tenantServices.getTenantsByMemberKey(storedUser.userKey()).stream()
            .map(entity -> new TenantDTO(entity.key(), entity.tenantId(), entity.name()))
            .toList();

    return aCamundaUser()
        .withUserKey(storedUser.userKey())
        .withName(storedUser.name())
        .withUsername(storedUser.username())
        .withPassword(storedUser.password())
        .withEmail(storedUser.email())
        .withAuthorizedApplications(authorizedApplications)
        .withTenants(tenants)
        .build();
  }
}

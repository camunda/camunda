/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder.aCamundaUser;

import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.UserServices;
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

  public CamundaUserDetailsService(
      final UserServices userServices,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices,
      final TenantServices tenantServices) {
    this.userServices = userServices;
    this.authorizationServices = authorizationServices;
    this.roleServices = roleServices;
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

    final Long userKey = storedUser.userKey();

    final var roles = roleServices.findAll(RoleQuery.of(q -> q.filter(f -> f.memberKey(userKey))));

    final var authorizedApplications =
        authorizationServices.getAuthorizedApplications(
            Stream.concat(
                    roles.stream().map(r -> r.roleKey().toString()),
                    Stream.of(storedUser.username()))
                .collect(Collectors.toSet()));

    final var tenants =
        tenantServices.getTenantsByMemberId(username).stream()
            .map(
                entity ->
                    new TenantDTO(
                        entity.key(), entity.tenantId(), entity.name(), entity.description()))
            .toList();

    return aCamundaUser()
        .withUserKey(userKey)
        .withName(storedUser.name())
        .withUsername(storedUser.username())
        .withPassword(storedUser.password())
        .withEmail(storedUser.email())
        .withAuthorizedApplications(authorizedApplications)
        .withRoles(roles)
        .withTenants(tenants)
        .withCanLogout(true)
        .build();
  }
}

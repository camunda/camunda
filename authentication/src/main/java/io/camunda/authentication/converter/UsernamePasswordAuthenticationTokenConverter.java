/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class UsernamePasswordAuthenticationTokenConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final TenantServices tenantServices;

  public UsernamePasswordAuthenticationTokenConverter(
      final RoleServices roleServices,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    this.tenantServices = tenantServices;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return Optional.ofNullable(authentication)
        .filter(UsernamePasswordAuthenticationToken.class::isInstance)
        .isPresent();
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var username = authentication.getName();
    final var resolver = new Resolver(username);

    return CamundaAuthentication.of(
        a ->
            a.user(username)
                .groupIdsSupplier(resolver::groups)
                .roleIdsSupplier(resolver::roles)
                .tenantsSupplier(resolver::tenants));
  }

  /**
   * Per-authentication resolver that memoizes prerequisite lookups so that reads of multiple
   * membership fields on the same {@link CamundaAuthentication} share the groups→roles→tenants
   * chain. Synchronized so concurrent reads on different lazy fields don't double-fetch.
   */
  private final class Resolver {
    private final EnumMap<EntityType, Set<String>> ownerTypeToIds = new EnumMap<>(EntityType.class);

    private List<String> groups;
    private List<String> roles;
    private List<String> tenants;

    Resolver(final String username) {
      ownerTypeToIds.put(USER, Set.of(username));
    }

    synchronized List<String> groups() {
      if (groups == null) {
        final var ids =
            groupServices
                .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
                .stream()
                .map(GroupEntity::groupId)
                .collect(Collectors.toSet());

        if (!ids.isEmpty()) {
          ownerTypeToIds.put(GROUP, ids);
        }
        groups = List.copyOf(ids);
      }
      return groups;
    }

    synchronized List<String> roles() {
      if (roles == null) {
        groups();

        final var ids =
            roleServices
                .getRolesByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
                .stream()
                .map(RoleEntity::roleId)
                .collect(Collectors.toSet());

        if (!ids.isEmpty()) {
          ownerTypeToIds.put(ROLE, ids);
        }
        roles = List.copyOf(ids);
      }
      return roles;
    }

    synchronized List<String> tenants() {
      if (tenants == null) {
        roles();

        tenants =
            tenantServices
                .getTenantsByMemberTypeAndMemberIds(
                    ownerTypeToIds, CamundaAuthentication.anonymous())
                .stream()
                .map(TenantEntity::tenantId)
                .toList();
      }
      return tenants;
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnSecondaryStorageEnabled
public class DefaultBasicMembershipService implements BasicMembershipService {

  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;

  public DefaultBasicMembershipService(
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices) {
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
  }

  @Override
  public MembershipResolver newResolver(final String username) {
    return new Resolver(username);
  }

  /**
   * Per-authentication resolver that memoizes the {@code groups → roles → tenants} chain so
   * repeated reads on the same authentication share work. Synchronized so concurrent reads on
   * different lazy fields don't double-fetch. Mapping rules are never resolved for BASIC auth — the
   * converter does not wire the supplier and {@link #mappingRules()} returns an empty list.
   */
  private final class Resolver implements MembershipResolver {
    private final EnumMap<EntityType, Set<String>> ownerTypeToIds = new EnumMap<>(EntityType.class);

    private List<String> groups;
    private List<String> roles;
    private List<String> tenants;

    Resolver(final String username) {
      ownerTypeToIds.put(EntityType.USER, Set.of(username));
    }

    @Override
    public synchronized List<String> groups() {
      if (groups == null) {
        final var ids =
            groupServices
                .getGroupsByMemberTypeAndMemberIds(
                    ownerTypeToIds, CamundaAuthentication.anonymous())
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

    @Override
    public synchronized List<String> roles() {
      if (roles == null) {
        groups();

        final var ids =
            roleServices
                .getRolesByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
                .stream()
                .map(RoleEntity::roleId)
                .collect(Collectors.toSet());

        if (!ids.isEmpty()) {
          ownerTypeToIds.put(EntityType.ROLE, ids);
        }
        roles = List.copyOf(ids);
      }
      return roles;
    }

    @Override
    public synchronized List<String> tenants() {
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

    @Override
    public List<String> mappingRules() {
      return List.of();
    }
  }
}

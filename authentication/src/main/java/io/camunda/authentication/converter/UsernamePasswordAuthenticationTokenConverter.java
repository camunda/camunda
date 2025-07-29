/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
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
import java.util.HashMap;
import java.util.Map;
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
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();
    final var username = authentication.getName();
    ownerTypeToIds.put(USER, Set.of(username));

    final var groups =
        groupServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(GroupEntity::groupId)
            .collect(Collectors.toSet());

    if (!groups.isEmpty()) {
      ownerTypeToIds.put(GROUP, groups);
    }

    final var roles =
        roleServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(ROLE, roles);
    }

    final var tenants =
        tenantServices
            .withAuthentication(CamundaAuthentication.anonymous())
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds)
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return CamundaAuthentication.of(
        a ->
            a.user(username)
                .roleIds(roles.stream().toList())
                .groupIds(groups.stream().toList())
                .tenants(tenants)
                .claims(Map.of(AUTHORIZED_USERNAME, username)));
  }
}

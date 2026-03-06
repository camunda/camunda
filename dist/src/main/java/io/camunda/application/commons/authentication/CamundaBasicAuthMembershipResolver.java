/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.BasicAuthMembershipResolver;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "camunda.auth.sdk.enabled", havingValue = "true")
@ConditionalOnAnyHttpGatewayEnabled
@ConditionalOnSecondaryStorageEnabled
public class CamundaBasicAuthMembershipResolver implements BasicAuthMembershipResolver {

  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final TenantServices tenantServices;

  public CamundaBasicAuthMembershipResolver(
      final RoleServices roleServices,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    this.tenantServices = tenantServices;
  }

  @Override
  public CamundaAuthentication resolveMemberships(final String username) {
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();
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
                .tenants(tenants));
  }
}

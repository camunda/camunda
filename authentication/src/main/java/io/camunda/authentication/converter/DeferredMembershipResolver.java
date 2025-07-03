/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import static io.camunda.zeebe.protocol.record.value.EntityType.CLIENT;
import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class DeferredMembershipResolver {

  private static final Logger LOG = LoggerFactory.getLogger(DeferredMembershipResolver.class);

  private final MappingServices mappingServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;

  public DeferredMembershipResolver(
      final MappingServices mappingServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices) {
    this.mappingServices = mappingServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
  }

  public MembershipContext resolveMemberships(final CamundaAuthentication authentication) {

    final var attributes = authentication.getClaims();
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    final var username = authentication.getUsername();
    if (StringUtils.hasText(username)) {
      ownerTypeToIds.put(USER, Set.of(username));
    }

    final var clientId = authentication.getClientId();
    if (StringUtils.hasText(clientId)) {
      ownerTypeToIds.put(CLIENT, Set.of(clientId));
    }

    final var mappingIds = getMappingIds(attributes);
    if (!CollectionUtils.isEmpty(mappingIds)) {
      ownerTypeToIds.put(MAPPING, new HashSet<>(mappingIds));
    }

    final List<String> groupIds;
    if (CollectionUtils.isEmpty(authentication.getGroupIds())) {
      groupIds = getGroupIds(ownerTypeToIds);
    } else {
      groupIds = authentication.getGroupIds();
    }

    if (!CollectionUtils.isEmpty(groupIds)) {
      ownerTypeToIds.put(GROUP, new HashSet<>(groupIds));
    }

    final var roleIds = getRoleIds(ownerTypeToIds);
    if (!CollectionUtils.isEmpty(roleIds)) {
      ownerTypeToIds.put(EntityType.ROLE, new HashSet<>(roleIds));
    }

    final var tenantIds = getTenantIds(ownerTypeToIds);
    return new MembershipContext(groupIds, roleIds, tenantIds, mappingIds);
  }

  protected List<String> getMappingIds(final Map<String, Object> attributes) {
    if (!CollectionUtils.isEmpty(attributes)) {
      return mappingServices
          .withAuthentication(CamundaAuthentication.anonymous())
          .getMatchingMappings(attributes)
          .map(MappingEntity::mappingId)
          .toList();
    } else {
      return null;
    }
  }

  protected List<String> getRoleIds(final Map<EntityType, Set<String>> ownerTypeToIds) {
    if (!CollectionUtils.isEmpty(ownerTypeToIds)) {
      return roleServices
          .withAuthentication(CamundaAuthentication.anonymous())
          .getRolesByMemberTypeAndMemberIds(ownerTypeToIds)
          .stream()
          .map(RoleEntity::roleId)
          .toList();
    } else {
      return null;
    }
  }

  protected List<String> getTenantIds(final Map<EntityType, Set<String>> ownerTypeToIds) {
    if (!CollectionUtils.isEmpty(ownerTypeToIds)) {
      return tenantServices
          .withAuthentication(CamundaAuthentication.anonymous())
          .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds)
          .stream()
          .map(TenantEntity::tenantId)
          .collect(Collectors.toList());
    } else {
      return null;
    }
  }

  protected List<String> getGroupIds(final Map<EntityType, Set<String>> ownerTypeToIds) {
    if (!CollectionUtils.isEmpty(ownerTypeToIds)) {
      return groupServices
          .withAuthentication(CamundaAuthentication.anonymous())
          .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds)
          .stream()
          .map(GroupEntity::groupId)
          .toList();
    } else {
      return null;
    }
  }
}

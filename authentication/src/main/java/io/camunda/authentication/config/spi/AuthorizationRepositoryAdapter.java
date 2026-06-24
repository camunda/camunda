/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.Authorization;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.authz.ResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.port.out.AuthorizationRepositoryPort;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host-supplied {@link AuthorizationRepositoryPort} that adapts OC's authorization data store to
 * the library's data shape. The library's {@code ResourcePermissionService} consumes the returned
 * records to answer "does this principal have permission P on resource R of type T?".
 *
 * <p>Owner-id resolution mirrors {@code io.camunda.security.core.authz.AuthorizationChecker}:
 * direct grants on the user/client plus grants reachable via the principal's groups, roles, and
 * mapping rules. Lookups bypass tenant/resource access checks because this port is consulted to
 * compute those checks itself ({@link ResourceAccessChecks#disabled()}).
 *
 * <p>The reader is resolved per request: {@code findAuthorizations} reads from the {@link
 * AuthorizationReader} of the in-context physical tenant, selected from {@link
 * PhysicalTenantSearchClientReaders} by {@link PhysicalTenantContext#current()}.
 */
public class AuthorizationRepositoryAdapter implements AuthorizationRepositoryPort {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationRepositoryAdapter.class);

  private final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders;

  public AuthorizationRepositoryAdapter(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders) {
    this.physicalTenantSearchClientReaders = physicalTenantSearchClientReaders;
  }

  @Override
  public Set<Authorization> findAuthorizations(
      final CamundaAuthentication authentication, final ResourceType resourceType) {
    final var ownerTypeToOwnerIds = collectOwnerTypeToOwnerIds(authentication);
    if (ownerTypeToOwnerIds.isEmpty()) {
      LOG.trace(
          "findAuthorizations: no owner ids resolved for principal, resourceType={}", resourceType);
      return Set.of();
    }

    LOG.trace(
        "findAuthorizations: resourceType={} ownerTypeToOwnerIds={}",
        resourceType,
        ownerTypeToOwnerIds);

    final var query =
        AuthorizationQuery.of(
            q ->
                q.filter(
                        f ->
                            f.ownerTypeToOwnerIds(ownerTypeToOwnerIds)
                                .resourceType(resourceType.name()))
                    .unlimited());

    final String physicalTenantId = PhysicalTenantContext.current();
    final var readersByPhysicalTenant = physicalTenantSearchClientReaders.readersByPhysicalTenant();
    final SearchClientReaders searchClientReaders = readersByPhysicalTenant.get(physicalTenantId);
    if (searchClientReaders == null) {
      throw new IllegalStateException(
          "No AuthorizationReader registered for physical tenant '%s'; known physical tenants: %s"
              .formatted(physicalTenantId, readersByPhysicalTenant.keySet()));
    }
    final AuthorizationReader authorizationReader = searchClientReaders.authorizationReader();
    final var entities = authorizationReader.search(query, ResourceAccessChecks.disabled()).items();
    LOG.trace(
        "findAuthorizations: resourceType={} matched {} authorization record(s)",
        resourceType,
        entities.size());

    // Aggregate by resourceId so multiple grant rows on the same resource fold into a single
    // Authorization with the union of permissions.
    final Map<String, Set<PermissionType>> permissionsByResourceId = new LinkedHashMap<>();
    for (final AuthorizationEntity entity : entities) {
      final String resourceId = entity.resourceId();
      if (resourceId == null) {
        LOG.warn(
            "Skipping authorization record with null resourceId for resourceType={} ownerId={}",
            entity.resourceType(),
            entity.ownerId());
        continue;
      }
      final var permissions =
          permissionsByResourceId.computeIfAbsent(resourceId, k -> new HashSet<>());
      entity.permissionTypes().stream()
          .map(AuthorizationRepositoryAdapter::toLibrary)
          .forEach(permissions::add);
    }

    return permissionsByResourceId.entrySet().stream()
        .map(e -> new Authorization(resourceType, e.getKey(), Set.copyOf(e.getValue())))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Map<EntityType, Set<String>> collectOwnerTypeToOwnerIds(
      final CamundaAuthentication authentication) {
    final var ownerTypeToOwnerIds = new HashMap<EntityType, Set<String>>();
    if (authentication.authenticatedUsername() != null) {
      ownerTypeToOwnerIds.put(EntityType.USER, Set.of(authentication.authenticatedUsername()));
    }
    if (authentication.authenticatedClientId() != null) {
      ownerTypeToOwnerIds.put(EntityType.CLIENT, Set.of(authentication.authenticatedClientId()));
    }
    final var groupIds = authentication.authenticatedGroupIds();
    if (groupIds != null && !groupIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.GROUP, new HashSet<>(groupIds));
    }
    final var roleIds = authentication.authenticatedRoleIds();
    if (roleIds != null && !roleIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.ROLE, new HashSet<>(roleIds));
    }
    final var mappingRuleIds = authentication.authenticatedMappingRuleIds();
    if (mappingRuleIds != null && !mappingRuleIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.MAPPING_RULE, new HashSet<>(mappingRuleIds));
    }
    return ownerTypeToOwnerIds;
  }

  // The library's ResourceType / PermissionType were lifted verbatim from zeebe-protocol per
  // ADR-0007
  // (https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0007-resource-permission-port-and-authorization-repository.md)
  // — name() values match one-to-one. valueOf will throw at runtime if a value drifts;
  // AuthorizationRepositoryAdapterTest asserts the full mapping to fail-fast on drift.
  static ResourceType toLibrary(final AuthorizationResourceType ocType) {
    return ResourceType.valueOf(ocType.name());
  }

  static PermissionType toLibrary(final PermissionType permission) {
    return permission;
  }
}

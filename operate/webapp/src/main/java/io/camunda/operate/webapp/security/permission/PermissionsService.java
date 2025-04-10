/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.permission;

import io.camunda.authentication.entity.CamundaPrincipal;
import io.camunda.authentication.entity.CamundaUser;
import io.camunda.operate.webapp.security.tenant.TenantService;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class PermissionsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PermissionsService.class);

  private final SecurityConfiguration securityConfiguration;
  private final AuthorizationChecker authorizationChecker;
  private final TenantService tenantService;

  public PermissionsService(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker,
      final TenantService tenantService) {
    this.securityConfiguration = securityConfiguration;
    this.authorizationChecker = authorizationChecker;
    this.tenantService = tenantService;
  }

  /**
   * getProcessDefinitionPermissions
   *
   * @param bpmnProcessId bpmnProcessId
   * @return permissions the user has for the given bpmnProcessId
   */
  public Set<String> getProcessDefinitionPermissions(final String bpmnProcessId) {
    return getResourcePermissions(bpmnProcessId, AuthorizationResourceType.PROCESS_DEFINITION);
  }

  /**
   * getDecisionDefinitionPermissions
   *
   * @param decisionId decisionId
   * @return permissions the user has for the given decisionId
   */
  public Set<String> getDecisionDefinitionPermissions(final String decisionId) {
    return getResourcePermissions(decisionId, AuthorizationResourceType.DECISION_DEFINITION);
  }

  /**
   * getResourcePermissions
   *
   * @param resourceKey resourceKey
   * @param resourceType resourceType
   * @return permissions the user has for the given resource
   */
  public Set<String> getResourcePermissions(
      final String resourceKey, final AuthorizationResourceType resourceType) {
    final Set<String> permissions = new HashSet<>();
    if (isAuthorized()) {
      final Set<PermissionType> permissionTypeSet =
          authorizationChecker.collectPermissionTypes(
              resourceKey, resourceType, getAuthentication());
      permissionTypeSet.forEach(p -> permissions.add(p.name()));
    }

    return permissions;
  }

  /**
   * hasPermissionForResource
   *
   * @return true if the user has the given permission for the process
   */
  public boolean hasPermissionForResource(
      final Long deploymentKey, final PermissionType permissionType) {
    return hasPermissionForResourceType(
        deploymentKey.toString(), AuthorizationResourceType.RESOURCE, permissionType);
  }

  /**
   * hasPermissionForProcess
   *
   * @return true if the user has the given permission for the process
   */
  public boolean hasPermissionForProcess(
      final String bpmnProcessId, final PermissionType permissionType) {
    return hasPermissionForResourceType(
        bpmnProcessId, AuthorizationResourceType.PROCESS_DEFINITION, permissionType);
  }

  /**
   * hasPermissionForDecision
   *
   * @return true if the user has the given permission for the decision
   */
  public boolean hasPermissionForDecision(
      final String decisionId, final PermissionType permissionType) {
    return hasPermissionForResourceType(
        decisionId, AuthorizationResourceType.DECISION_DEFINITION, permissionType);
  }

  /**
   * hasPermissionForResource
   *
   * @return true if the user has the given permission for the resource
   */
  private boolean hasPermissionForResourceType(
      final String resourceId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    if (!permissionsEnabled()) {
      return true;
    }
    if (!isAuthorized()) {
      return false;
    }
    return isAuthorizedFor(resourceId, resourceType, permissionType);
  }

  /**
   * getProcessesWithPermission
   *
   * @return processes for which the user has the given permission; the result matches either all
   *     processes, or a list of bpmnProcessId
   */
  public ResourcesAllowed getProcessesWithPermission(final PermissionType permissionType) {
    return getResourcesWithPermission(AuthorizationResourceType.PROCESS_DEFINITION, permissionType);
  }

  /**
   * getDecisionsWithPermission
   *
   * @return decisions for which the user has the given permission; the result matches either all
   *     decisions, or a list of decisionId
   */
  public ResourcesAllowed getDecisionsWithPermission(final PermissionType permissionType) {
    return getResourcesWithPermission(
        AuthorizationResourceType.DECISION_DEFINITION, permissionType);
  }

  /**
   * getResourcesWithPermission
   *
   * @return resources for which the user has the given permission; the result matches either all
   *     resources, or a list of resourceIds
   */
  private ResourcesAllowed getResourcesWithPermission(
      final AuthorizationResourceType resourceType, final PermissionType permissionType) {
    if (!permissionsEnabled()) {
      return ResourcesAllowed.all();
    }
    if (!isAuthorized()) {
      return ResourcesAllowed.withIds(Set.of());
    }

    final Authorization authorization = new Authorization(resourceType, permissionType);
    final SecurityContext securityContext = getSecurityContext(authorization);
    final List<String> ids = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (hasWildcardPermission(ids)) {
      return ResourcesAllowed.all();
    }

    return ResourcesAllowed.withIds(new LinkedHashSet<>(ids));
  }

  private boolean hasWildcardPermission(final List<String> resourceKeys) {
    return resourceKeys != null && resourceKeys.contains("*");
  }

  /**
   * @return true if permissions checks are enabled
   */
  public boolean permissionsEnabled() {
    return securityConfiguration.getAuthorizations().isEnabled();
  }

  private boolean isAuthorized() {
    return (getAuthenticatedUsername() != null);
  }

  private String getAuthenticatedUsername() {
    final Authentication requestAuthentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (requestAuthentication != null) {
      final Object principal = requestAuthentication.getPrincipal();
      if (principal instanceof final CamundaUser authenticatedPrincipal) {
        return authenticatedPrincipal.getUsername();
      }
    }
    return null;
  }

  private List<Long> getAuthenticatedUserRoleKeys() {
    final Authentication requestAuthentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (requestAuthentication != null) {
      final Object principal = requestAuthentication.getPrincipal();
      if (principal instanceof final CamundaPrincipal authenticatedPrincipal) {
        return authenticatedPrincipal.getAuthenticationContext().roles().stream()
            .map(RoleEntity::roleKey)
            .toList();
      }
    }
    return Collections.emptyList();
  }

  private List<String> getAuthenticatedUserTenantIds() {
    final Authentication requestAuthentication =
        SecurityContextHolder.getContext().getAuthentication();
    if (requestAuthentication != null) {
      final Object principal = requestAuthentication.getPrincipal();
      if (principal instanceof final CamundaPrincipal authenticatedPrincipal) {
        return authenticatedPrincipal.getAuthenticationContext().tenants().stream()
            .map(TenantDTO::tenantId)
            .toList();
      }
    }
    return Collections.emptyList();
  }

  private boolean isAuthorizedFor(
      final String resourceId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final Authorization authorization = new Authorization(resourceType, permissionType);
    final SecurityContext securityContext = getSecurityContext(authorization);
    return authorizationChecker.isAuthorized(resourceId, securityContext);
  }

  private SecurityContext getSecurityContext(final Authorization authorization) {
    return new SecurityContext(getAuthentication(), authorization);
  }

  private io.camunda.security.auth.Authentication getAuthentication() {
    final var authenticatedUsername = getAuthenticatedUsername();
    final List<Long> authenticatedRoleKeys = getAuthenticatedUserRoleKeys();
    final List<String> authenticatedTenantIds = getAuthenticatedUserTenantIds();
    // groups  will come later
    return new io.camunda.security.auth.Authentication.Builder()
        .user(authenticatedUsername)
        .roleKeys(authenticatedRoleKeys)
        .tenants(authenticatedTenantIds)
        .build();
  }

  /** ResourcesAllowed */
  public static final class ResourcesAllowed {
    private final boolean all;
    private final Set<String> ids;

    private ResourcesAllowed(final boolean all, final Set<String> ids) {
      this.all = all;
      this.ids = ids;
    }

    public static ResourcesAllowed all() {
      return new ResourcesAllowed(true, null);
    }

    public static ResourcesAllowed withIds(final Set<String> ids) {
      return new ResourcesAllowed(false, ids);
    }

    @Override
    public int hashCode() {
      return Objects.hash(all, ids);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ResourcesAllowed that = (ResourcesAllowed) o;
      return all == that.all && Objects.equals(ids, that.ids);
    }

    /**
     * isAll
     *
     * @return true if all resources are allowed, false if only the ids are allowed
     */
    public boolean isAll() {
      return all;
    }

    /**
     * getIds
     *
     * @return ids of resources allowed in case not all are allowed
     */
    public Set<String> getIds() {
      return ids;
    }
  }
}

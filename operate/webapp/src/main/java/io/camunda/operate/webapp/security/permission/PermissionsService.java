/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.permission;

import io.camunda.operate.webapp.api.v1.exceptions.ForbiddenException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class PermissionsService {

  private final SecurityConfiguration securityConfiguration;
  private final AuthorizationChecker authorizationChecker;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ResourceAccessProvider resourceAccessProvider;

  public PermissionsService(
      final SecurityConfiguration securityConfiguration,
      final AuthorizationChecker authorizationChecker,
      final ResourceAccessProvider resourceAccessProvider,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.securityConfiguration = securityConfiguration;
    this.authorizationChecker = authorizationChecker;
    this.resourceAccessProvider = resourceAccessProvider;
    this.authenticationProvider = authenticationProvider;
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

  public void verifyWildcardResourcePermission(
      final AuthorizationResourceType resourceType, final PermissionType permissionType) {
    if (!hasPermissionForResourceType(
        AuthorizationScope.WILDCARD_CHAR, resourceType, permissionType)) {
      throw new ForbiddenException(
          "%s:%s:%s permissions required to access this resource."
              .formatted(
                  resourceType.toString(),
                  AuthorizationScope.WILDCARD_CHAR,
                  permissionType.toString()));
    }
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
      return ResourcesAllowed.wildcard();
    }
    if (!isAuthorized()) {
      return ResourcesAllowed.withIds(Set.of());
    }

    final var authorization =
        Authorization.of(a -> a.resourceType(resourceType).permissionType(permissionType));
    final var resourceAccess =
        resourceAccessProvider.resolveResourceAccess(getAuthentication(), authorization);

    if (resourceAccess.wildcard()) {
      return ResourcesAllowed.wildcard();
    } else if (resourceAccess.denied()) {
      return ResourcesAllowed.withIds(Set.of());
    }

    final var ids = resourceAccess.authorization().resourceIds();
    return ResourcesAllowed.withIds(new LinkedHashSet<>(ids));
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
    return Optional.ofNullable(getAuthentication())
        .map(CamundaAuthentication::authenticatedUsername)
        .orElse(null);
  }

  private boolean isAuthorizedFor(
      final String resourceId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var authorization =
        Authorization.of(a -> a.resourceType(resourceType).permissionType(permissionType));
    return resourceAccessProvider
        .hasResourceAccessByResourceId(getAuthentication(), authorization, resourceId)
        .allowed();
  }

  private CamundaAuthentication getAuthentication() {
    return authenticationProvider.getCamundaAuthentication();
  }

  /** ResourcesAllowed */
  public record ResourcesAllowed(boolean all, Set<String> ids) {

    public static ResourcesAllowed wildcard() {
      return new ResourcesAllowed(true, null);
    }

    public static ResourcesAllowed withIds(final Set<String> ids) {
      return new ResourcesAllowed(false, ids);
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

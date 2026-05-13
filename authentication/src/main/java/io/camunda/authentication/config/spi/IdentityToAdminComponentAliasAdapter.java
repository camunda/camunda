/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.PermissionType;
import io.camunda.security.api.model.ResourceType;
import io.camunda.security.core.port.in.ResourcePermissionPort;
import io.camunda.security.core.port.out.AuthorizationRepositoryPort;
import io.camunda.security.spring.security.ResourcePermissionService;

/**
 * Host {@link ResourcePermissionPort} that delegates to CSL's {@link ResourcePermissionService} for
 * every check except a single OC-specific carve-out: a grant on the legacy {@code identity}
 * component is treated as access to the {@code admin} webapp.
 *
 * <p>The alias is intentionally not in CSL — it is a backward-compatibility surface OC carries
 * forward from before the {@code identity} component was renamed to {@code admin}. Wildcard {@code
 * "*"} grants and exact resource-id matching are CSL's responsibility (handled by the wrapped
 * {@link ResourcePermissionService}; see <a
 * href="https://github.com/camunda/camunda-security-library/issues/179">CSL #179</a>).
 */
public class IdentityToAdminComponentAliasAdapter implements ResourcePermissionPort {

  private static final String COMPONENT_ADMIN = "admin";
  private static final String COMPONENT_IDENTITY_LEGACY_ALIAS = "identity";

  private final ResourcePermissionPort delegate;

  public IdentityToAdminComponentAliasAdapter(
      final AuthorizationRepositoryPort authorizationRepository) {
    this.delegate = new ResourcePermissionService(authorizationRepository);
  }

  @Override
  public boolean hasPermission(
      final CamundaAuthentication authentication,
      final ResourceType resourceType,
      final String resourceId,
      final PermissionType permissionType) {
    if (resourceType == ResourceType.COMPONENT
        && permissionType == PermissionType.ACCESS
        && COMPONENT_ADMIN.equals(resourceId)
        && delegate.hasPermission(
            authentication, resourceType, COMPONENT_IDENTITY_LEGACY_ALIAS, permissionType)) {
      return true;
    }
    return delegate.hasPermission(authentication, resourceType, resourceId, permissionType);
  }
}

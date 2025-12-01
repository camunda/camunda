/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.request;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Map;
import java.util.Set;

public record AuthorizationRequestMetadata(
    Map<String, Object> claims,
    AuthorizationResourceType resourceType,
    PermissionType permissionType,
    boolean isNewResource,
    boolean isTenantOwnedResource,
    String tenantId,
    Set<AuthorizationScope> authorizationScopes) {

  public String getForbiddenErrorMessage() {
    final var authorizationScopesContainsOnlyWildcard =
        authorizationScopes.size() == 1
            && authorizationScopes.contains(AuthorizationScope.WILDCARD);
    return authorizationScopesContainsOnlyWildcard
        ? AuthorizationCheckBehavior.FORBIDDEN_ERROR_MESSAGE.formatted(permissionType, resourceType)
        : AuthorizationCheckBehavior.FORBIDDEN_ERROR_MESSAGE_WITH_RESOURCE.formatted(
            permissionType,
            resourceType,
            authorizationScopes.stream()
                .filter(scope -> scope.getResourceId() != null && !scope.getResourceId().isEmpty())
                .map(AuthorizationScope::getResourceId)
                .sorted()
                .toList());
  }

  public String getTenantErrorMessage() {
    final var errorMsg =
        isNewResource
            ? AuthorizationCheckBehavior.FORBIDDEN_FOR_TENANT_ERROR_MESSAGE
            : AuthorizationCheckBehavior.NOT_FOUND_FOR_TENANT_ERROR_MESSAGE;
    return errorMsg.formatted(permissionType, resourceType, tenantId);
  }
}

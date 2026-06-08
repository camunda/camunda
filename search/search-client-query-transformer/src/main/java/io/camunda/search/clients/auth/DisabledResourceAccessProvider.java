/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.security.api.model.authz.AuthorizationScope.WILDCARD;
import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.reader.ResourceAccess;
import io.camunda.security.core.reader.ResourceAccessProvider;

public class DisabledResourceAccessProvider implements ResourceAccessProvider {

  @Override
  public <T> ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<T> requiredAuthorization) {
    return ResourceAccess.wildcard(
        withRequiredAuthorization(requiredAuthorization, WILDCARD.getResourceId()));
  }

  @Override
  public <T> ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<T> requiredAuthorization,
      final T resource) {
    return ResourceAccess.wildcard(
        withRequiredAuthorization(requiredAuthorization, WILDCARD.getResourceId()));
  }

  @Override
  public <T> ResourceAccess hasResourceAccessByResourceId(
      final CamundaAuthentication authentication,
      final RequiredAuthorization<T> requiredAuthorization,
      final String resourceId) {
    return ResourceAccess.wildcard(
        withRequiredAuthorization(requiredAuthorization, WILDCARD.getResourceId()));
  }
}

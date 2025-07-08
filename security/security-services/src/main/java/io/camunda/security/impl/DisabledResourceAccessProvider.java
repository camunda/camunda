/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.clients.security.ResourceAccess;
import io.camunda.search.clients.security.ResourceAccessProvider;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;

public class DisabledResourceAccessProvider implements ResourceAccessProvider {

  @Override
  public ResourceAccess resolveResourcesAccess(
      final CamundaAuthentication authentication, final Authorization<?> requiredAuthorization) {
    return ResourceAccess.wildcard(requiredAuthorization);
  }

  @Override
  public <T> ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final T resource) {
    return ResourceAccess.wildcard(requiredAuthorization);
  }

  @Override
  public ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final Authorization<?> requiredAuthorization,
      final String resourceId) {
    return ResourceAccess.wildcard(requiredAuthorization);
  }
}

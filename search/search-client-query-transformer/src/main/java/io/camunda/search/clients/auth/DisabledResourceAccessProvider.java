/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.security.auth.Authorization.WILDCARD;
import static io.camunda.security.auth.Authorization.withAuthorization;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;

public class DisabledResourceAccessProvider implements ResourceAccessProvider {

  @Override
  public <T> ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization<T> requiredAuthorization) {
    return ResourceAccess.wildcard(withAuthorization(requiredAuthorization, WILDCARD));
  }

  @Override
  public <T> ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final T resource) {
    return ResourceAccess.wildcard(withAuthorization(requiredAuthorization, WILDCARD));
  }

  @Override
  public <T> ResourceAccess hasResourceAccessByResourceId(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final String resourceId) {
    return ResourceAccess.wildcard(withAuthorization(requiredAuthorization, WILDCARD));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.reader;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;

public interface ResourceAccessProvider {

  <T> ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization<T> requiredAuthorization);

  <T> ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      Authorization<T> requiredAuthorization,
      final T resource);

  <T> ResourceAccess hasResourceAccessByResourceId(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final String resourceId);
}

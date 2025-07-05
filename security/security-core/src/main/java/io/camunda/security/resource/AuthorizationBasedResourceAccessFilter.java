/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.resource;

import io.camunda.security.auth.Authorization;

public record AuthorizationBasedResourceAccessFilter(
    boolean granted, boolean requiresCheck, Authorization requiredAuthorization) {

  public boolean forbidden() {
    return !granted && !requiresCheck;
  }

  public static AuthorizationBasedResourceAccessFilter successful() {
    return new AuthorizationBasedResourceAccessFilter(true, false, null);
  }

  public static AuthorizationBasedResourceAccessFilter unsuccessful() {
    return new AuthorizationBasedResourceAccessFilter(false, false, null);
  }

  public static AuthorizationBasedResourceAccessFilter requiredAuthorizationCheck(
      final Authorization requiredAuthorization) {
    return new AuthorizationBasedResourceAccessFilter(false, true, requiredAuthorization);
  }
}

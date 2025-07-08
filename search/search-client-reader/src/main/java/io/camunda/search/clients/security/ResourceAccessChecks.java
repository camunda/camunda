/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import java.util.List;

public record ResourceAccessChecks(
    CamundaAuthentication authentication,
    AuthorizationChecks authorizationChecks,
    TenantChecks tenantChecks) {

  public static ResourceAccessChecks disabled() {
    return new ResourceAccessChecks(
        null, AuthorizationChecks.notRequired(), TenantChecks.notRequired());
  }

  public static ResourceAccessChecks with(
      final CamundaAuthentication authentication,
      final AuthorizationChecks authorizationChecks,
      final TenantChecks tenantChecks) {
    return new ResourceAccessChecks(authentication, authorizationChecks, tenantChecks);
  }

  public record AuthorizationChecks(boolean required, Authorization<?> authorization) {

    public static AuthorizationChecks notRequired() {
      return new AuthorizationChecks(false, null);
    }

    public static AuthorizationChecks required(final Authorization<?> authorization) {
      return new AuthorizationChecks(true, authorization);
    }
  }

  public record TenantChecks(boolean required, List<String> tenantIds) {

    public static TenantChecks notRequired() {
      return new TenantChecks(false, null);
    }

    public static TenantChecks required(final List<String> tenantIds) {
      return new TenantChecks(true, tenantIds);
    }
  }
}

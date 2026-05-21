/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PhysicalTenantRedirectUriRewriter {

  private static final String LOGIN_CALLBACK = "/login/oauth2/code/";

  private PhysicalTenantRedirectUriRewriter() {}

  public static String rewrite(final String template, final String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    final String prefixed = "/physical-tenant/" + tenantId + LOGIN_CALLBACK;
    if (template.contains(prefixed)) {
      return template;
    }
    return template.replace(LOGIN_CALLBACK, prefixed);
  }
}

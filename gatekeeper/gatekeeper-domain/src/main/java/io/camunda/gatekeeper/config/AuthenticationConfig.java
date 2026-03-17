/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.config;

import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import java.time.Duration;

/** Immutable authentication configuration for the domain layer. */
public record AuthenticationConfig(
    AuthenticationMethod method,
    Duration authenticationRefreshInterval,
    boolean unprotectedApi,
    OidcConfig oidc) {

  /** Returns true if local (database-managed) groups are enabled, i.e. not sourced from an IdP. */
  public boolean isCamundaGroupsEnabled() {
    return !(method == AuthenticationMethod.OIDC && oidc != null && oidc.isGroupsClaimConfigured());
  }

  /** Returns true if local (database-managed) users are enabled, i.e. not using OIDC. */
  public boolean isCamundaUsersEnabled() {
    return method != AuthenticationMethod.OIDC;
  }
}

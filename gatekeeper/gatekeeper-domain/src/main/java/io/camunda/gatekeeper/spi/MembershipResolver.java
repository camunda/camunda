/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spi;

import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.gatekeeper.model.identity.PrincipalType;
import java.util.Map;

/**
 * SPI for resolving group, role, tenant, and mapping rule memberships from token claims.
 *
 * <p>Consumers implement this interface to connect to their own user store. The default
 * implementation extracts groups from JWT claims only.
 */
public interface MembershipResolver {

  /**
   * Resolves the full authentication context (groups, roles, tenants, mapping rules) for the given
   * principal based on their token claims.
   *
   * @param tokenClaims the JWT/OIDC token claims
   * @param principalId the principal identifier (username or client ID)
   * @param principalType whether the principal is a user or a client
   * @return a fully-resolved {@link CamundaAuthentication}
   */
  CamundaAuthentication resolveMemberships(
      Map<String, Object> tokenClaims, String principalId, PrincipalType principalType);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.security;

import io.camunda.identity.sdk.tenants.dto.Tenant;
import java.util.List;

@FunctionalInterface
public interface TenantProvider {
  /**
   * Returns the list of tenants which are assigned to the user linked to the token.
   *
   * @param token the token
   * @return the list of tenants
   */
  List<Tenant> forToken(final String token);
}

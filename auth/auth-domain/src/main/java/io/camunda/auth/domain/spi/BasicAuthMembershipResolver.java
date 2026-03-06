/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.spi;

import io.camunda.auth.domain.model.CamundaAuthentication;

/** Abstracts DB-based membership resolution for locally-authenticated users. */
public interface BasicAuthMembershipResolver {
  /** Resolves groups, roles, and tenants for a locally-authenticated username. */
  CamundaAuthentication resolveMemberships(String username);
}

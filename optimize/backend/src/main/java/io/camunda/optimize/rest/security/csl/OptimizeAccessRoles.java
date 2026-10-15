/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

/** POC: the fixed meaning of roles. Holding any role grants access; {@code admin} administers. */
public final class OptimizeAccessRoles {

  public static final String ADMIN_ROLE_ID = "admin";
  public static final String ACCESS_ROLES_ENABLED_PROPERTY =
      "optimize.security.access-roles.enabled";

  private OptimizeAccessRoles() {}
}

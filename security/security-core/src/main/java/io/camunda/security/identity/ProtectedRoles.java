/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.identity;

import java.util.Set;

/**
 * System-protected role IDs that must not be updated or deleted, and whose authorizations must not
 * be deleted. The Identity UI loads the same list via the {@code /admin/config.js} client config so
 * both sides stay in sync.
 */
public final class ProtectedRoles {

  public static final Set<String> PROTECTED_ROLE_IDS =
      Set.of("admin", "readonly-admin", "rpa", "connectors", "app-integrations", "task-worker");

  private ProtectedRoles() {}

  public static boolean isProtected(final String roleId) {
    return roleId != null && PROTECTED_ROLE_IDS.contains(roleId);
  }
}

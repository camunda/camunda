/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.core.port.out.AdminUserPresencePort;
import io.camunda.service.RoleServices;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host-supplied {@link AdminUserPresencePort}. Mirrors the presence check formerly performed by the
 * host {@code AdminUserCheckFilter}: an admin user is considered provisioned when either the
 * configured admin role carries a non-empty user-members list in the initialization config, or the
 * admin role has at least one user member in the live store.
 *
 * <p>Errors raised while consulting the live store (e.g. secondary storage outage) are logged and
 * answered as {@code true} so the library's setup-redirect filter does not block traffic during
 * transient failures, matching the existing {@code AdminUserCheckFilter} behavior.
 */
public class AdminUserPresenceAdapter implements AdminUserPresencePort {

  private static final Logger LOG = LoggerFactory.getLogger(AdminUserPresenceAdapter.class);
  private static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  private static final String USER_MEMBERS = "users";

  private final RoleServices roleServices;
  private final SecurityConfiguration securityConfiguration;

  public AdminUserPresenceAdapter(
      final RoleServices roleServices, final SecurityConfiguration securityConfiguration) {
    this.roleServices = roleServices;
    this.securityConfiguration = securityConfiguration;
  }

  @Override
  public boolean adminUserExists() {
    final var hasConfiguredAdminUser =
        !securityConfiguration
            .getInitialization()
            .getDefaultRoles()
            .getOrDefault(ADMIN_ROLE_ID, Map.of())
            .getOrDefault(USER_MEMBERS, Set.of())
            .isEmpty();
    if (hasConfiguredAdminUser) {
      return true;
    }

    try {
      return roleServices.hasMembersOfType(
          ADMIN_ROLE_ID, EntityType.USER, CamundaAuthentication.anonymous(), "default");
    } catch (final RuntimeException ex) {
      LOG.error(
          "Error while searching for admin role members. This might indicate that secondary storage is down.",
          ex);
      // Mirror AdminUserCheckFilter: do not block traffic on transient errors.
      return true;
    }
  }
}

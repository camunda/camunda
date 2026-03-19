/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.spi.AdminUserCheckProvider;
import io.camunda.gatekeeper.model.identity.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class AdminUserCheckAdapter implements AdminUserCheckProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AdminUserCheckAdapter.class);
  private static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  private static final String USER_MEMBERS = "users";

  private final SecurityConfiguration securityConfiguration;
  private final RoleServices roleServices;

  public AdminUserCheckAdapter(
      final SecurityConfiguration securityConfiguration, final RoleServices roleServices) {
    this.securityConfiguration = securityConfiguration;
    this.roleServices = roleServices;
  }

  @Override
  public boolean hasAdminUser() {
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
          ADMIN_ROLE_ID, EntityType.USER, CamundaAuthentication.anonymous());
    } catch (final RuntimeException ex) {
      LOG.error(
          "Error while searching for admin role members. "
              + "This might indicate that secondary storage is down.",
          ex);
      return true; // Assume admin exists to avoid redirect loops
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.spi.AdminUserCheckProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "camunda.auth.method")
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaAdminUserCheckProvider implements AdminUserCheckProvider {

  private static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  private static final String USER_MEMBERS = "users";

  private final SecurityConfiguration securityConfig;
  private final RoleServices roleServices;

  public CamundaAdminUserCheckProvider(
      final SecurityConfiguration securityConfig, final RoleServices roleServices) {
    this.securityConfig = securityConfig;
    this.roleServices = roleServices;
  }

  @Override
  public boolean hasAdminUser() {
    final var hasConfiguredAdminUser =
        !securityConfig
            .getInitialization()
            .getDefaultRoles()
            .getOrDefault(ADMIN_ROLE_ID, Map.of())
            .getOrDefault(USER_MEMBERS, Set.of())
            .isEmpty();

    if (hasConfiguredAdminUser) {
      return true;
    }

    return roleServices
        .withAuthentication(CamundaAuthentication.anonymous())
        .hasMembersOfType(ADMIN_ROLE_ID, EntityType.USER);
  }
}

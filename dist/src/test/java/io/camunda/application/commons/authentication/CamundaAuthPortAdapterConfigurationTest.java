/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.auth.domain.port.inbound.AuthorizationManagementPort;
import io.camunda.auth.domain.port.inbound.GroupManagementPort;
import io.camunda.auth.domain.port.inbound.MappingRuleManagementPort;
import io.camunda.auth.domain.port.inbound.RoleManagementPort;
import io.camunda.auth.domain.port.inbound.TenantManagementPort;
import io.camunda.auth.domain.port.inbound.UserManagementPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * Tests for {@link CamundaAuthPortAdapterConfiguration}. The configuration uses several conditional
 * annotations:
 *
 * <ul>
 *   <li>Class-level: {@code @ConditionalOnProperty(name = "camunda.auth.method")}
 *   <li>Class-level: {@code @ConditionalOnAnyHttpGatewayEnabled} (needs REST or MCP gateway)
 *   <li>Class-level: {@code @ConditionalOnSecondaryStorageEnabled} (needs
 *       camunda.data.secondary-storage.type != "none")
 *   <li>Bean-level: {@code @ConditionalOnInternalUserManagement} (camunda.auth.method != "oidc")
 *   <li>Bean-level: {@code @ConditionalOnCamundaGroupsEnabled} (camunda.auth.oidc.groups-claim is
 *       empty)
 * </ul>
 */
class CamundaAuthPortAdapterConfigurationTest {

  /** Creates a runner with all mock beans but no properties -- caller adds properties as needed. */
  private WebApplicationContextRunner runnerWithBeans() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(CamundaAuthPortAdapterConfiguration.class)
        .withBean(UserServices.class, () -> mock(UserServices.class))
        .withBean(RoleServices.class, () -> mock(RoleServices.class))
        .withBean(GroupServices.class, () -> mock(GroupServices.class))
        .withBean(TenantServices.class, () -> mock(TenantServices.class))
        .withBean(MappingRuleServices.class, () -> mock(MappingRuleServices.class))
        .withBean(AuthorizationServices.class, () -> mock(AuthorizationServices.class))
        .withBean(
            CamundaAuthenticationProvider.class, () -> mock(CamundaAuthenticationProvider.class));
  }

  /**
   * Creates a base runner with all required mock service beans and properties that satisfy the
   * class-level conditions:
   *
   * <ul>
   *   <li>camunda.auth.method=basic (satisfies @ConditionalOnProperty)
   *   <li>camunda.rest.enabled=true (satisfies @ConditionalOnAnyHttpGatewayEnabled via REST
   *       gateway)
   *   <li>camunda.data.secondary-storage.type=elasticsearch
   *       (satisfies @ConditionalOnSecondaryStorageEnabled)
   * </ul>
   */
  private WebApplicationContextRunner baseRunner() {
    return runnerWithBeans()
        .withPropertyValues(
            "camunda.auth.method=basic",
            "camunda.rest.enabled=true",
            "camunda.data.secondary-storage.type=elasticsearch");
  }

  @Test
  void allPortBeansCreatedWhenAllConditionsMet() {
    baseRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(UserManagementPort.class);
              assertThat(context).hasSingleBean(RoleManagementPort.class);
              assertThat(context).hasSingleBean(GroupManagementPort.class);
              assertThat(context).hasSingleBean(TenantManagementPort.class);
              assertThat(context).hasSingleBean(MappingRuleManagementPort.class);
              assertThat(context).hasSingleBean(AuthorizationManagementPort.class);
            });
  }

  @Test
  void noPortBeansWhenAuthMethodNotConfigured() {
    runnerWithBeans()
        .withPropertyValues(
            "camunda.rest.enabled=true", "camunda.data.secondary-storage.type=elasticsearch")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(UserManagementPort.class);
              assertThat(context).doesNotHaveBean(RoleManagementPort.class);
              assertThat(context).doesNotHaveBean(GroupManagementPort.class);
              assertThat(context).doesNotHaveBean(TenantManagementPort.class);
              assertThat(context).doesNotHaveBean(MappingRuleManagementPort.class);
              assertThat(context).doesNotHaveBean(AuthorizationManagementPort.class);
            });
  }

  @Test
  void noPortBeansWhenRestGatewayDisabledAndMcpDisabled() {
    baseRunner()
        .withPropertyValues("camunda.rest.enabled=false", "camunda.mcp.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(UserManagementPort.class);
              assertThat(context).doesNotHaveBean(RoleManagementPort.class);
            });
  }

  @Test
  void noPortBeansWhenSecondaryStorageDisabled() {
    baseRunner()
        .withPropertyValues("camunda.data.secondary-storage.type=none")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(UserManagementPort.class);
              assertThat(context).doesNotHaveBean(RoleManagementPort.class);
            });
  }

  @Test
  void userManagementPortNotCreatedWhenAuthMethodIsOidc() {
    // @ConditionalOnInternalUserManagement evaluates: auth.method != 'oidc'
    // When method=oidc, UserManagementPort should NOT be created
    baseRunner()
        .withPropertyValues("camunda.auth.method=oidc")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(UserManagementPort.class);
              // Other beans should still exist
              assertThat(context).hasSingleBean(RoleManagementPort.class);
              assertThat(context).hasSingleBean(TenantManagementPort.class);
              assertThat(context).hasSingleBean(MappingRuleManagementPort.class);
              assertThat(context).hasSingleBean(AuthorizationManagementPort.class);
            });
  }

  @Test
  void groupManagementPortNotCreatedWhenGroupsClaimConfigured() {
    // @ConditionalOnCamundaGroupsEnabled evaluates: oidc.groups-claim == ''
    // When groups-claim is set, GroupManagementPort should NOT be created
    baseRunner()
        .withPropertyValues("camunda.auth.oidc.groups-claim=groups")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(GroupManagementPort.class);
              // Other beans should still exist
              assertThat(context).hasSingleBean(UserManagementPort.class);
              assertThat(context).hasSingleBean(RoleManagementPort.class);
              assertThat(context).hasSingleBean(TenantManagementPort.class);
              assertThat(context).hasSingleBean(MappingRuleManagementPort.class);
              assertThat(context).hasSingleBean(AuthorizationManagementPort.class);
            });
  }

  @Test
  void oidcWithGroupsClaimCreatesNeitherUserNorGroupPorts() {
    baseRunner()
        .withPropertyValues("camunda.auth.method=oidc", "camunda.auth.oidc.groups-claim=groups")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(UserManagementPort.class);
              assertThat(context).doesNotHaveBean(GroupManagementPort.class);
              // The rest should still be present
              assertThat(context).hasSingleBean(RoleManagementPort.class);
              assertThat(context).hasSingleBean(TenantManagementPort.class);
              assertThat(context).hasSingleBean(MappingRuleManagementPort.class);
              assertThat(context).hasSingleBean(AuthorizationManagementPort.class);
            });
  }

  @Test
  void portBeansCreatedWithMcpGatewayInsteadOfRestGateway() {
    baseRunner()
        .withPropertyValues("camunda.rest.enabled=false", "camunda.mcp.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RoleManagementPort.class);
              assertThat(context).hasSingleBean(TenantManagementPort.class);
            });
  }

  @Test
  void portBeansCreatedWithRdbmsSecondaryStorage() {
    baseRunner()
        .withPropertyValues("camunda.data.secondary-storage.type=rdbms")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RoleManagementPort.class);
              assertThat(context).hasSingleBean(AuthorizationManagementPort.class);
            });
  }
}

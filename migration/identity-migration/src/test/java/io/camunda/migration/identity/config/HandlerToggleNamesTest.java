/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.saas.SaaSMigrationHandlerConfig;
import io.camunda.migration.identity.config.sm.SMKeycloakMigrationHandlerConfig;
import io.camunda.migration.identity.config.sm.SMOidcMigrationHandlerConfig;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.migration.identity.handler.saas.AuthorizationMigrationHandler;
import io.camunda.migration.identity.handler.saas.ClientMigrationHandler;
import io.camunda.migration.identity.handler.saas.GroupMigrationHandler;
import io.camunda.migration.identity.handler.saas.StaticConsoleRoleAuthorizationMigrationHandler;
import io.camunda.migration.identity.handler.saas.StaticConsoleRoleMigrationHandler;
import io.camunda.migration.identity.handler.sm.MappingRuleMigrationHandler;
import io.camunda.migration.identity.handler.sm.RoleMigrationHandler;
import io.camunda.migration.identity.handler.sm.TenantMigrationHandler;
import io.camunda.migration.identity.handler.sm.UserRoleMigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies that every handler-toggle property constant declared on the {@code *HandlerConfig}
 * classes actually controls the right bean. A typo in either the constant or the {@link
 * org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} usage would surface as a
 * failure here, before integration.
 */
class HandlerToggleNamesTest {

  private static Stream<Arguments> toggles() {
    return Stream.of(
        // SaaS / cloud
        Arguments.of(
            "CLOUD",
            SaaSMigrationHandlerConfig.class,
            SaaSMigrationHandlerConfig.GROUP_ENABLED,
            GroupMigrationHandler.class),
        Arguments.of(
            "CLOUD",
            SaaSMigrationHandlerConfig.class,
            SaaSMigrationHandlerConfig.CONSOLE_ROLE_ENABLED,
            StaticConsoleRoleMigrationHandler.class),
        Arguments.of(
            "CLOUD",
            SaaSMigrationHandlerConfig.class,
            SaaSMigrationHandlerConfig.CONSOLE_ROLE_AUTHORIZATION_ENABLED,
            StaticConsoleRoleAuthorizationMigrationHandler.class),
        Arguments.of(
            "CLOUD",
            SaaSMigrationHandlerConfig.class,
            SaaSMigrationHandlerConfig.AUTHORIZATION_ENABLED,
            AuthorizationMigrationHandler.class),
        Arguments.of(
            "CLOUD",
            SaaSMigrationHandlerConfig.class,
            SaaSMigrationHandlerConfig.CLIENT_ENABLED,
            ClientMigrationHandler.class),
        // SM Keycloak
        Arguments.of(
            "KEYCLOAK",
            SMKeycloakMigrationHandlerConfig.class,
            SMKeycloakMigrationHandlerConfig.ROLE_ENABLED,
            RoleMigrationHandler.class),
        Arguments.of(
            "KEYCLOAK",
            SMKeycloakMigrationHandlerConfig.class,
            SMKeycloakMigrationHandlerConfig.GROUP_ENABLED,
            io.camunda.migration.identity.handler.sm.GroupMigrationHandler.class),
        Arguments.of(
            "KEYCLOAK",
            SMKeycloakMigrationHandlerConfig.class,
            SMKeycloakMigrationHandlerConfig.USER_ROLE_ENABLED,
            UserRoleMigrationHandler.class),
        Arguments.of(
            "KEYCLOAK",
            SMKeycloakMigrationHandlerConfig.class,
            SMKeycloakMigrationHandlerConfig.CLIENT_ENABLED,
            io.camunda.migration.identity.handler.sm.ClientMigrationHandler.class),
        Arguments.of(
            "KEYCLOAK",
            SMKeycloakMigrationHandlerConfig.class,
            SMKeycloakMigrationHandlerConfig.AUTHORIZATION_ENABLED,
            io.camunda.migration.identity.handler.sm.AuthorizationMigrationHandler.class),
        Arguments.of(
            "KEYCLOAK",
            SMKeycloakMigrationHandlerConfig.class,
            SMKeycloakMigrationHandlerConfig.TENANT_ENABLED,
            TenantMigrationHandler.class),
        // SM OIDC
        Arguments.of(
            "OIDC",
            SMOidcMigrationHandlerConfig.class,
            SMOidcMigrationHandlerConfig.ROLE_ENABLED,
            RoleMigrationHandler.class),
        Arguments.of(
            "OIDC",
            SMOidcMigrationHandlerConfig.class,
            SMOidcMigrationHandlerConfig.TENANT_ENABLED,
            TenantMigrationHandler.class),
        Arguments.of(
            "OIDC",
            SMOidcMigrationHandlerConfig.class,
            SMOidcMigrationHandlerConfig.MAPPING_RULE_ENABLED,
            MappingRuleMigrationHandler.class));
  }

  @ParameterizedTest(name = "[{index}] {2} disables {3}")
  @MethodSource("toggles")
  void disablingToggleRemovesHandlerBean(
      final String mode,
      final Class<?> handlerConfig,
      final String propertyKey,
      final Class<? extends MigrationHandler<?>> handlerClass) {
    new ApplicationContextRunner()
        .withConfiguration(
            org.springframework.boot.autoconfigure.AutoConfigurations.of(
                handlerConfig, MockDependencies.class))
        .withPropertyValues("camunda.migration.identity.mode=" + mode, propertyKey + "=false")
        .run(context -> assertThat(context).doesNotHaveBean(handlerClass));
  }

  @org.springframework.boot.test.context.TestConfiguration
  static class MockDependencies {
    @org.springframework.context.annotation.Bean
    CamundaAuthentication camundaAuthentication() {
      return mock(CamundaAuthentication.class);
    }

    @org.springframework.context.annotation.Bean
    ManagementIdentityClient managementIdentityClient() {
      return mock(ManagementIdentityClient.class);
    }

    @org.springframework.context.annotation.Bean
    RoleServices roleServices() {
      return mock(RoleServices.class);
    }

    @org.springframework.context.annotation.Bean
    AuthorizationServices authorizationServices() {
      return mock(AuthorizationServices.class);
    }

    @org.springframework.context.annotation.Bean
    GroupServices groupServices() {
      return mock(GroupServices.class);
    }

    @org.springframework.context.annotation.Bean
    TenantServices tenantServices() {
      return mock(TenantServices.class);
    }

    @org.springframework.context.annotation.Bean
    MappingRuleServices mappingRuleServices() {
      return mock(MappingRuleServices.class);
    }

    @org.springframework.context.annotation.Bean
    IdentityMigrationProperties identityMigrationProperties() {
      return mock(IdentityMigrationProperties.class);
    }

    @org.springframework.context.annotation.Bean
    ConsoleClient consoleClient() {
      return mock(ConsoleClient.class);
    }
  }
}

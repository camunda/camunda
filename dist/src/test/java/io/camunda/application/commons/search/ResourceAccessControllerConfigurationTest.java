/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.application.commons.security.CamundaSecurityConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.search.clients.auth.DefaultTenantAccessProvider;
import io.camunda.search.clients.auth.DisabledTenantAccessProvider;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.TenantAccessProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

public class ResourceAccessControllerConfigurationTest {

  private WebApplicationContextRunner baseRunner() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(
            ResourceAccessControllerConfiguration.class,
            CamundaSecurityConfiguration.class,
            UnifiedConfiguration.class,
            UnifiedConfigurationHelper.class)
        .withBean(AuthorizationChecker.class, () -> mock(AuthorizationChecker.class))
        // make REST gateway condition pass
        .withPropertyValues(
            "zeebe.broker.gateway.enable=true",
            "camunda.rest.enabled=true",
            // avoid needing AuthorizationChecker by disabling authorizations
            "camunda.security.authorizations.enabled=false",
            // choose a supported secondary storage type for document-based RAC beans
            "camunda.database.type=elasticsearch");
  }

  @Test
  public void camelCaseEnabledCreatesDefaultTenantProvider() {
    baseRunner()
        .withPropertyValues("camunda.security.multiTenancy.checksEnabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TenantAccessProvider.class);
              final var tap = context.getBean(TenantAccessProvider.class);
              assertThat(tap).isInstanceOf(DefaultTenantAccessProvider.class);
            });
  }

  @Test
  public void camelCaseDisabledCreatesDisabledTenantProvider() {
    baseRunner()
        .withPropertyValues("camunda.security.multiTenancy.checksEnabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TenantAccessProvider.class);
              final var tap = context.getBean(TenantAccessProvider.class);
              assertThat(tap).isInstanceOf(DisabledTenantAccessProvider.class);
            });
  }

  @Test
  public void kebabCaseEnabledCreatesDefaultTenantProvider() {
    baseRunner()
        .withPropertyValues("camunda.security.multi-tenancy.checks-enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TenantAccessProvider.class);
              final var tap = context.getBean(TenantAccessProvider.class);
              assertThat(tap).isInstanceOf(DefaultTenantAccessProvider.class);
            });
  }

  @Test
  public void kebabCaseDisabledCreatesDisabledTenantProvider() {
    baseRunner()
        .withPropertyValues("camunda.security.multi-tenancy.checks-enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(TenantAccessProvider.class);
              final var tap = context.getBean(TenantAccessProvider.class);
              assertThat(tap).isInstanceOf(DisabledTenantAccessProvider.class);
            });
  }

  @Test
  public void missingPropertyCreatesDisabledTenantProvider() {
    baseRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(TenantAccessProvider.class);
              final var tap = context.getBean(TenantAccessProvider.class);
              assertThat(tap).isInstanceOf(DisabledTenantAccessProvider.class);
            });
  }
}

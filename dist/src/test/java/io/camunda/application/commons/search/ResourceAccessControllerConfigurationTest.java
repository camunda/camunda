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
import static org.mockito.Mockito.when;

import io.camunda.application.commons.security.CamundaSecurityConfiguration;
import io.camunda.application.commons.security.PhysicalTenantSecurityProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.search.clients.auth.DefaultTenantAccessProvider;
import io.camunda.search.clients.auth.DisabledTenantAccessProvider;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.security.core.authz.TenantAccessProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

public class ResourceAccessControllerConfigurationTest {

  private WebApplicationContextRunner baseRunner() {
    final var authReaderStub = mock(AuthorizationReader.class);
    final var readersStub = mock(SearchClientReaders.class);
    when(readersStub.authorizationReader()).thenReturn(authReaderStub);
    final var defaultPtReaders =
        new PhysicalTenantSearchClientReaders(Map.of("default", readersStub));
    final var defaultPtSecProps =
        new PhysicalTenantSecurityProperties(
            Map.of("default", new CamundaSecurityLibraryProperties()));

    return new WebApplicationContextRunner()
        .withUserConfiguration(
            ResourceAccessControllerConfiguration.class,
            CamundaSecurityConfiguration.class,
            UnifiedConfiguration.class,
            UnifiedConfigurationHelper.class)
        .withBean(AuthorizationChecker.class, () -> mock(AuthorizationChecker.class))
        .withBean(PhysicalTenantSearchClientReaders.class, () -> defaultPtReaders)
        .withBean(PhysicalTenantSecurityProperties.class, () -> defaultPtSecProps)
        // make REST gateway condition pass
        .withPropertyValues(
            "zeebe.broker.gateway.enable=true",
            "camunda.rest.enabled=true",
            // avoid needing AuthorizationChecker by disabling authorizations
            "camunda.security.authorizations.enabled=false");
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

  @Nested
  class PhysicalTenantResourceAccessControllersBean {

    // Creates a dedicated runner with two PT entries — avoids bean-override conflict with
    // baseRunner
    private WebApplicationContextRunner twoTenantRunner(final String storageType) {
      final var authReaderA = mock(AuthorizationReader.class);
      final var authReaderB = mock(AuthorizationReader.class);
      final var readersA = mock(SearchClientReaders.class);
      final var readersB = mock(SearchClientReaders.class);
      when(readersA.authorizationReader()).thenReturn(authReaderA);
      when(readersB.authorizationReader()).thenReturn(authReaderB);
      final var ptReaders =
          new PhysicalTenantSearchClientReaders(Map.of("tenanta", readersA, "tenantb", readersB));
      final var ptSecProps =
          new PhysicalTenantSecurityProperties(
              Map.of(
                  "tenanta",
                  new CamundaSecurityLibraryProperties(),
                  "tenantb",
                  new CamundaSecurityLibraryProperties()));

      return new WebApplicationContextRunner()
          .withUserConfiguration(
              ResourceAccessControllerConfiguration.class,
              CamundaSecurityConfiguration.class,
              UnifiedConfiguration.class,
              UnifiedConfigurationHelper.class)
          .withBean(AuthorizationChecker.class, () -> mock(AuthorizationChecker.class))
          .withBean(PhysicalTenantSearchClientReaders.class, () -> ptReaders)
          .withBean(PhysicalTenantSecurityProperties.class, () -> ptSecProps)
          .withPropertyValues(
              "zeebe.broker.gateway.enable=true",
              "camunda.rest.enabled=true",
              "camunda.security.authorizations.enabled=false",
              "camunda.data.secondary-storage.type=" + storageType);
    }

    @Test
    void shouldContainEntryForEachPhysicalTenantForElasticsearch() {
      twoTenantRunner("elasticsearch")
          .run(
              context -> {
                assertThat(context).hasSingleBean(PhysicalTenantResourceAccessControllers.class);
                final var controllers =
                    context.getBean(PhysicalTenantResourceAccessControllers.class);
                assertThat(controllers.controllersByPhysicalTenant())
                    .containsOnlyKeys("tenanta", "tenantb");
                assertThat(controllers.controllersByPhysicalTenant().values())
                    .allSatisfy(c -> assertThat(c).isInstanceOf(ResourceAccessController.class));
              });
    }

    @Test
    void shouldContainEntryForEachPhysicalTenantForOpensearch() {
      twoTenantRunner("opensearch")
          .run(
              context -> {
                assertThat(context).hasSingleBean(PhysicalTenantResourceAccessControllers.class);
                final var controllers =
                    context.getBean(PhysicalTenantResourceAccessControllers.class);
                assertThat(controllers.controllersByPhysicalTenant())
                    .containsOnlyKeys("tenanta", "tenantb");
                assertThat(controllers.controllersByPhysicalTenant().values())
                    .allSatisfy(c -> assertThat(c).isInstanceOf(ResourceAccessController.class));
              });
    }
  }
}

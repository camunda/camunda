/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityMigrationPropertiesValidationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void whenAllRequiredPropertiesProvidedValidationPasses() {
    contextRunner
        .withPropertyValues(
            "camunda.migration.identity.managementIdentity.client-id=test-client-id",
            "camunda.migration.identity.managementIdentity.client-secret=test-client-secret",
            "camunda.migration.identity.managementIdentity.issuer-backend-url=https://issuer.example.com",
            "camunda.migration.identity.managementIdentity.audience=test-audience",
            "camunda.migration.identity.managementIdentity.base-url=https://identity.example.com")
        .run(
            context -> {
              final IdentityMigrationProperties props =
                  context.getBean(IdentityMigrationProperties.class);
              assertThat(props.getOrganizationId()).isEqualTo("test-org");
              assertThat(props.getMode()).isEqualTo(IdentityMigrationProperties.Mode.CLOUD);

              final ManagementIdentityProperties identity = props.getManagementIdentity();
              assertThat(identity.getClientId()).isEqualTo("test-client-id");
              assertThat(identity.getClientSecret()).isEqualTo("test-client-secret");
              assertThat(identity.getIssuerBackendUrl()).isEqualTo("https://issuer.example.com");
              assertThat(identity.getAudience()).isEqualTo("test-audience");
              assertThat(identity.getBaseUrl()).isEqualTo("https://identity.example.com");
            });
  }

  @Test
  void whenRequiredPropertiesAreMissingContextFailsToStart() {
    contextRunner
        .withPropertyValues(
            "camunda.migration.identity.managementIdentity.client-id=test-client-id"
            // missing required managementIdentity properties
            )
        .run(
            context -> {
              assertThat(context).hasFailed();

              final Throwable startupFailure = context.getStartupFailure();
              assertThat(startupFailure)
                  .isInstanceOf(ConfigurationPropertiesBindException.class)
                  .hasRootCauseInstanceOf(BindValidationException.class);

              assertThat(startupFailure.getMessage()).contains("camunda.migration.identity");
            });
  }

  @Configuration
  @EnableConfigurationProperties(IdentityMigrationProperties.class)
  static class TestConfig {
    // No beans needed; this just enables property binding
  }
}

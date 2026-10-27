/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.headers.ContentSecurityPolicyConfig.Mode;
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the {@link SaasCspModeCompatibility} BeanPostProcessor flips the CSL CSP mode to {@link
 * Mode#SAAS} when OC's legacy {@code camunda.security.saas.organization-id} indicates a SaaS
 * deployment, leaves CSL's {@link Mode#SELF_MANAGED} default alone for self-managed setups, and
 * never overrides an operator-supplied mode.
 */
class SaasCspModeCompatibilityTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(CamundaSecurityConfiguration.class, SaasCspModeCompatibility.class)
          .withPropertyValues("spring.profiles.active=consolidated-auth");

  @Test
  void shouldSetSaasModeWhenSaasOrganizationIdIsConfigured() {
    runner
        .withPropertyValues("camunda.security.saas.organization-id=demo-org")
        .run(
            ctx -> {
              final var props = ctx.getBean(CamundaSecurityLibraryProperties.class);
              assertThat(props.getHttpHeaders().getContentSecurityPolicy().getMode())
                  .isEqualTo(Mode.SAAS);
            });
  }

  @Test
  void shouldAcceptCamelCaseSaasOrganizationIdKey() {
    runner
        .withPropertyValues("camunda.security.saas.organizationId=demo-org")
        .run(
            ctx -> {
              final var props = ctx.getBean(CamundaSecurityLibraryProperties.class);
              assertThat(props.getHttpHeaders().getContentSecurityPolicy().getMode())
                  .isEqualTo(Mode.SAAS);
            });
  }

  @Test
  void shouldKeepDefaultSelfManagedModeWhenSaasIsNotConfigured() {
    runner.run(
        ctx -> {
          final var props = ctx.getBean(CamundaSecurityLibraryProperties.class);
          assertThat(props.getHttpHeaders().getContentSecurityPolicy().getMode())
              .isEqualTo(Mode.SELF_MANAGED);
        });
  }

  @Test
  void shouldNotOverrideOperatorSuppliedMode() {
    runner
        .withPropertyValues(
            "camunda.security.saas.organization-id=demo-org",
            "camunda.security.http-headers.content-security-policy.mode=CUSTOM")
        .run(
            ctx -> {
              final var props = ctx.getBean(CamundaSecurityLibraryProperties.class);
              assertThat(props.getHttpHeaders().getContentSecurityPolicy().getMode())
                  .isEqualTo(Mode.CUSTOM);
            });
  }
}

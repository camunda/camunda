/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.optimize.rest.security.CustomPreAuthenticatedAuthenticationProvider;
import io.camunda.optimize.rest.security.ccsm.CCSMSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.cloud.CCSaaSSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.cloud.CCSaasAuth0WebSecurityConfig;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import io.camunda.optimize.tomcat.CCSMRequestAdjustmentFilter;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.SecurityPathPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;

/**
 * Verifies that exactly one security setup is active per {@code optimize.security.csl.enabled}
 * state, for both the CCSM and CCSaaS editions (ADR-0038). Default (property absent or {@code
 * true}, since camunda/camunda#58483) activates {@link OptimizeCamundaSecurityConfig} with the
 * legacy adapters (and, for CCSaaS, the Auth0 client-registration publisher) backed off; explicit
 * {@code false} is the escape hatch that flips this back to the legacy stack through 8.10
 * (camunda/camunda#58484).
 */
class CslSecurityChainSelectionTest {

  private static final String CSL_FLAG_ENABLED = "optimize.security.csl.enabled=true";
  private static final String CSL_FLAG_DISABLED = "optimize.security.csl.enabled=false";

  // Explicit static endpoints (no issuer-uri) so CSL builds the OIDC client registration without
  // any network/discovery call.
  private static final String[] STATIC_OIDC_PROPERTIES = {
    "camunda.security.authentication.method=oidc",
    "camunda.security.authentication.oidc.client-id=test-client",
    "camunda.security.authentication.oidc.client-secret=test-secret",
    "camunda.security.authentication.oidc.authorization-uri=https://idp.example.com/authorize",
    "camunda.security.authentication.oidc.token-uri=https://idp.example.com/token",
    "camunda.security.authentication.oidc.jwk-set-uri=https://idp.example.com/jwks"
  };

  private final ApplicationContextRunner ccsmRunner =
      new ApplicationContextRunner()
          .withPropertyValues("spring.profiles.active=ccsm")
          .withBean(
              LazyInitializationBeanFactoryPostProcessor.class,
              LazyInitializationBeanFactoryPostProcessor::new)
          .withBean(
              ConfigurationService.class, ConfigurationServiceBuilder::createDefaultConfiguration)
          .withBean(
              CustomPreAuthenticatedAuthenticationProvider.class,
              () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
          .withBean(SessionService.class, () -> mock(SessionService.class))
          .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
          .withBean(CCSMTokenService.class, () -> mock(CCSMTokenService.class))
          .withUserConfiguration(
              CCSMSecurityConfigurerAdapter.class, OptimizeCamundaSecurityConfig.class);

  private final ApplicationContextRunner ccsaasRunner =
      new ApplicationContextRunner()
          .withPropertyValues("spring.profiles.active=cloud")
          .withBean(
              LazyInitializationBeanFactoryPostProcessor.class,
              LazyInitializationBeanFactoryPostProcessor::new)
          .withBean(ConfigurationService.class, CslSecurityChainSelectionTest::cloudConfiguration)
          .withBean(
              CustomPreAuthenticatedAuthenticationProvider.class,
              () -> mock(CustomPreAuthenticatedAuthenticationProvider.class))
          .withBean(SessionService.class, () -> mock(SessionService.class))
          .withBean(AuthCookieService.class, () -> mock(AuthCookieService.class))
          .withBean(UserIdMigrationService.class, () -> mock(UserIdMigrationService.class))
          .withUserConfiguration(
              CCSaaSSecurityConfigurerAdapter.class,
              CCSaasAuth0WebSecurityConfig.class,
              OptimizeCamundaSecurityConfig.class);

  private static ConfigurationService cloudConfiguration() {
    final ConfigurationService configurationService =
        ConfigurationServiceBuilder.createDefaultConfiguration();
    final var cloudAuthConfiguration =
        configurationService.getAuthConfiguration().getCloudAuthConfiguration();
    cloudAuthConfiguration.setClientId("auth0-client");
    cloudAuthConfiguration.setClientSecret("auth0-secret");
    return configurationService;
  }

  @Test
  void shouldActivateCslChainForCcsmWhenFlagAbsent() {
    ccsmRunner
        .withPropertyValues(STATIC_OIDC_PROPERTIES)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(CCSMSecurityConfigurerAdapter.class);
              assertThat(context).hasSingleBean(OptimizeCamundaSecurityConfig.class);
              assertThat(context.getBean(SecurityPathPort.class))
                  .isInstanceOf(OptimizeSecurityPathAdapter.class);
              assertThat(context.getBean(MembershipPort.class))
                  .isInstanceOf(OptimizeMembershipAdapter.class);
              assertExternalSharingFilterRegistered(context);
            });
  }

  @Test
  void shouldActivateCslChainForCcsmWhenFlagExplicitlyEnabled() {
    ccsmRunner
        .withPropertyValues(STATIC_OIDC_PROPERTIES)
        .withPropertyValues(CSL_FLAG_ENABLED)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(CCSMSecurityConfigurerAdapter.class);
              assertThat(context).hasSingleBean(OptimizeCamundaSecurityConfig.class);
              assertThat(context.getBean(SecurityPathPort.class))
                  .isInstanceOf(OptimizeSecurityPathAdapter.class);
              assertThat(context.getBean(MembershipPort.class))
                  .isInstanceOf(OptimizeMembershipAdapter.class);
              assertExternalSharingFilterRegistered(context);
            });
  }

  @Test
  void shouldActivateLegacyCcsmChainWhenFlagExplicitlyDisabled() {
    ccsmRunner
        .withPropertyValues(CSL_FLAG_DISABLED)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CCSMSecurityConfigurerAdapter.class);
              assertThat(context).doesNotHaveBean(OptimizeCamundaSecurityConfig.class);
              assertThat(context).doesNotHaveBean(SecurityPathPort.class);
              assertThat(context).doesNotHaveBean("externalSharingRequestAdjuster");
            });
  }

  @Test
  void shouldActivateCslChainForCcsaasWhenFlagAbsent() {
    ccsaasRunner
        .withPropertyValues(STATIC_OIDC_PROPERTIES)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(CCSaaSSecurityConfigurerAdapter.class);
              assertThat(context).doesNotHaveBean(CCSaasAuth0WebSecurityConfig.class);
              assertThat(context).hasSingleBean(OptimizeCamundaSecurityConfig.class);
              assertThat(context.getBean(MembershipPort.class))
                  .isInstanceOf(OptimizeMembershipAdapter.class);
              assertExternalSharingFilterRegistered(context);
            });
  }

  @Test
  void shouldActivateCslChainForCcsaasWhenFlagExplicitlyEnabled() {
    ccsaasRunner
        .withPropertyValues(STATIC_OIDC_PROPERTIES)
        .withPropertyValues(CSL_FLAG_ENABLED)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(CCSaaSSecurityConfigurerAdapter.class);
              assertThat(context).doesNotHaveBean(CCSaasAuth0WebSecurityConfig.class);
              assertThat(context).hasSingleBean(OptimizeCamundaSecurityConfig.class);
              assertThat(context.getBean(MembershipPort.class))
                  .isInstanceOf(OptimizeMembershipAdapter.class);
              assertExternalSharingFilterRegistered(context);
            });
  }

  @Test
  void shouldActivateLegacyCcsaasChainWhenFlagExplicitlyDisabled() {
    ccsaasRunner
        .withPropertyValues(CSL_FLAG_DISABLED)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(CCSaaSSecurityConfigurerAdapter.class);
              assertThat(context).hasSingleBean(CCSaasAuth0WebSecurityConfig.class);
              assertThat(context).doesNotHaveBean(OptimizeCamundaSecurityConfig.class);
            });
  }

  /**
   * External sharing is served by a request-adjustment filter that each legacy adapter registers
   * for itself. Both back off in CSL mode, so {@link OptimizeCamundaSecurityConfig} must register
   * one, ahead of the security chain, which matches on the rewritten URI.
   */
  private static void assertExternalSharingFilterRegistered(final ApplicationContext context) {
    final FilterRegistrationBean<?> registration =
        context.getBean("externalSharingRequestAdjuster", FilterRegistrationBean.class);
    assertThat(registration.getFilter()).isInstanceOf(CCSMRequestAdjustmentFilter.class);
    assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    assertThat(registration.getUrlPatterns()).containsExactly("/*");
  }
}

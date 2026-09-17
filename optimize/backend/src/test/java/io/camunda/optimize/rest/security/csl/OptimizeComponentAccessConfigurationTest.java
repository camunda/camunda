/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizedComponentsPort;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import io.camunda.security.spring.spi.WebAppProviderPort;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;

class OptimizeComponentAccessConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OptimizeComponentAccessConfiguration.class));

  @Test
  void shouldRegisterTheAccessPortsWhenAnEditionPolicyIsPresent() {
    runner
        .withUserConfiguration(GrantingPolicyConfiguration.class)
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(OidcUserService.class)
                    .hasSingleBean(WebAppProviderPort.class)
                    .hasSingleBean(AuthorizationCheckPort.class)
                    .hasSingleBean(WebAppAccessDeniedHandlerPort.class)
                    .hasSingleBean(AuthorizedComponentsPort.class)
                    .hasSingleBean(SecurityHeadersCustomizer.class));
  }

  @Test
  void shouldBackOffWithoutAnEditionPolicy() {
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(WebAppProviderPort.class)
                .doesNotHaveBean(AuthorizationCheckPort.class));
  }

  @Test
  void shouldBackOffWhenCslIsDisabled() {
    runner
        .withUserConfiguration(GrantingPolicyConfiguration.class)
        .withPropertyValues("optimize.security.csl.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(WebAppProviderPort.class));
  }

  @Test
  void shouldReportOptimizeAsAuthorizedComponentWhenThePolicyAllows() {
    runner
        .withUserConfiguration(GrantingPolicyConfiguration.class)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(AuthorizedComponentsPort.class)
                            .resolve(CamundaAuthentication.of(builder -> builder.user("kermit"))))
                    .containsExactly("optimize"));
  }

  @Test
  void shouldReportNoAuthorizedComponentWhenThePolicyDenies() {
    runner
        .withUserConfiguration(DenyingPolicyConfiguration.class)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(AuthorizedComponentsPort.class)
                            .resolve(CamundaAuthentication.of(builder -> builder.user("kermit"))))
                    .isEmpty());
  }

  @Configuration
  static class GrantingPolicyConfiguration {

    @Bean
    OptimizeComponentAccessPolicy policy() {
      return new StubPolicy(Optional.empty());
    }
  }

  @Configuration
  static class DenyingPolicyConfiguration {

    @Bean
    OptimizeComponentAccessPolicy policy() {
      return new StubPolicy(Optional.of("no permission"));
    }
  }

  private record StubPolicy(Optional<String> denialReason)
      implements OptimizeComponentAccessPolicy {

    @Override
    public Optional<String> loginDenialReason(
        final String accessTokenValue, final Map<String, Object> claims) {
      return denialReason;
    }

    @Override
    public Optional<String> sessionDenialReason(final CamundaAuthentication authentication) {
      return denialReason;
    }
  }
}

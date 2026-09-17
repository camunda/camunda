/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.Either;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizedComponentsPort;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.filter.WebAppAuthorizationCheckFilter;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import io.camunda.security.spring.security.WebAppAuthorizationFilterConfiguration;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import io.camunda.security.spring.spi.WebAppProviderPort;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OptimizeComponentAccessConfigurationTest {

  private static final OptimizeComponentAccessPolicy GRANT = new StubPolicy(Either.right(null));
  private static final OptimizeComponentAccessPolicy DENY =
      new StubPolicy(Either.left("no permission"));

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OptimizeComponentAccessConfiguration.class))
          .withBean(CamundaAuthenticationProvider.class, () -> () -> null);

  @Test
  void shouldRegisterTheAccessPortsWhenAnEditionPolicyIsPresent() {
    runner
        .withBean(OptimizeComponentAccessPolicy.class, () -> GRANT)
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(AuthorizationCheckPort.class)
                    .hasSingleBean(AuthorizedComponentsPort.class)
                    .hasSingleBean(SecurityHeadersCustomizer.class));
  }

  @Test
  void shouldBackOffWithoutAnEditionPolicy() {
    runner.run(
        context ->
            assertThat(context)
                .doesNotHaveBean(AuthorizationCheckPort.class)
                .doesNotHaveBean(SecurityHeadersCustomizer.class));
  }

  @Test
  void shouldBackOffWhenCslIsDisabled() {
    runner
        .withBean(OptimizeComponentAccessPolicy.class, () -> GRANT)
        .withPropertyValues("optimize.security.csl.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AuthorizationCheckPort.class));
  }

  @Test
  void shouldNotLetCslBuildItsOwnCheckFilter() {
    // given
    // CSL's filter exempts a request by the shape of its URI, which Optimize must not do, so the
    // configuration keeps the port that would make CSL build it out of the context.
    final var cslRunner =
        runner
            .withConfiguration(AutoConfigurations.of(WebAppAuthorizationFilterConfiguration.class))
            .withBean(OptimizeComponentAccessPolicy.class, () -> GRANT)
            .withBean(SecurityPathPort.class, StubPathPort::new)
            .withBean(
                WebAppAccessDeniedHandlerPort.class, () -> (request, response, webApp, auth) -> {});

    // when
    // then
    cslRunner.run(
        context -> assertThat(context).doesNotHaveBean(WebAppAuthorizationCheckFilter.class));

    // A web app provider is the only condition left, so its absence above is what holds the filter
    // back.
    cslRunner
        .withBean(WebAppProviderPort.class, () -> request -> Optional.empty())
        .run(context -> assertThat(context).hasSingleBean(WebAppAuthorizationCheckFilter.class));
  }

  @Test
  void shouldReportOptimizeAsAuthorizedComponentWhenThePolicyAllows() {
    runner
        .withBean(OptimizeComponentAccessPolicy.class, () -> GRANT)
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
        .withBean(OptimizeComponentAccessPolicy.class, () -> DENY)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(AuthorizedComponentsPort.class)
                            .resolve(CamundaAuthentication.of(builder -> builder.user("kermit"))))
                    .isEmpty());
  }

  private record StubPathPort() implements SecurityPathPort {

    @Override
    public Set<String> apiPaths() {
      return Set.of();
    }

    @Override
    public Set<String> unprotectedApiPaths() {
      return Set.of();
    }

    @Override
    public Set<String> unprotectedPaths() {
      return Set.of();
    }

    @Override
    public Set<String> webappPaths() {
      return Set.of();
    }

    @Override
    public Set<String> webComponentNames() {
      return Set.of();
    }
  }

  private record StubPolicy(Either<String, Void> access) implements OptimizeComponentAccessPolicy {

    @Override
    public Either<String, Void> checkAccess(final CamundaAuthentication authentication) {
      return access;
    }
  }
}

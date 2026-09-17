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
import io.camunda.security.api.model.Either;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizedComponentsPort;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import io.camunda.security.spring.spi.WebAppProviderPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OptimizeComponentAccessConfigurationTest {

  private static final OptimizeComponentAccessPolicy GRANT = new StubPolicy(Either.right(null));
  private static final OptimizeComponentAccessPolicy DENY =
      new StubPolicy(Either.left("no permission"));

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OptimizeComponentAccessConfiguration.class));

  @Test
  void shouldRegisterTheAccessPortsWhenAnEditionPolicyIsPresent() {
    runner
        .withBean(OptimizeComponentAccessPolicy.class, () -> GRANT)
        .run(
            context ->
                assertThat(context)
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
        .withBean(OptimizeComponentAccessPolicy.class, () -> GRANT)
        .withPropertyValues("optimize.security.csl.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(WebAppProviderPort.class));
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

  private record StubPolicy(Either<String, Void> access) implements OptimizeComponentAccessPolicy {

    @Override
    public Either<String, Void> checkAccess(final CamundaAuthentication authentication) {
      return access;
    }
  }
}

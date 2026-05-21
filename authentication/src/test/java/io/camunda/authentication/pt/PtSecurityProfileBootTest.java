/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.WebSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies that {@link WebSecurityConfig}'s {@code @Profile("consolidated-auth & !pt-security")}
 * expression actually gates the host security configuration in and out as the {@code pt-security}
 * profile is toggled.
 *
 * <p>We boot a slice context containing only {@link WebSecurityConfig} and observe its lifecycle:
 *
 * <ul>
 *   <li>With {@code consolidated-auth} alone, the gate opens, the configuration class is processed,
 *       and Spring tries to satisfy {@code OidcOverrideBeansConfiguration}'s required {@code
 *       SecurityConfiguration} dependency. The slice deliberately does not provide that host SPI
 *       bean, so context startup fails. The failure proves the {@code @Import}/
 *       {@code @ImportAutoConfiguration} graph rooted at {@link WebSecurityConfig} was reached.
 *   <li>With {@code consolidated-auth} <em>and</em> {@code pt-security}, the {@code @Profile} gate
 *       closes, {@link WebSecurityConfig} (and the entire CSL umbrella it pulls in) is skipped, no
 *       downstream bean wiring is attempted, and the context starts cleanly.
 * </ul>
 *
 * <p>This is a profile-toggle smoke test, not a chain-construction test: wiring the full host
 * security graph (host SPI beans, {@code MembershipService}, {@code RoleServices}, {@code
 * SecurityConfiguration}) is out of scope here — that's what slice tests in {@code
 * io.camunda.authentication.config.controllers} already cover.
 */
class PtSecurityProfileBootTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(WebSecurityConfig.class)
          .withPropertyValues("camunda.security.authentication.method=oidc");

  @Test
  void shouldLoadWebSecurityConfigUnderConsolidatedAuthAlone() {
    // given - only consolidated-auth is active
    runner
        .withPropertyValues("spring.profiles.active=consolidated-auth")
        // when the context is built
        .run(
            ctx -> {
              // then WebSecurityConfig's @Profile gate opens; the import graph is reached and
              // fails to autowire because the slice deliberately omits the host
              // SecurityConfiguration
              // bean. The failure type/message proves WebSecurityConfig was actually processed.
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining(
                      "No qualifying bean of type "
                          + "'io.camunda.security.configuration.SecurityConfiguration'");
            });
  }

  @Test
  void shouldExcludeWebSecurityConfigWhenPtSecurityIsActive() {
    // given - both consolidated-auth and pt-security are active
    runner
        .withPropertyValues("spring.profiles.active=consolidated-auth,pt-security")
        // when the context is built
        .run(
            ctx -> {
              // then the @Profile gate closes; WebSecurityConfig is skipped along with its CSL
              // @ImportAutoConfiguration umbrella, so the context starts cleanly with no host
              // security beans registered
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).doesNotHaveBean(WebSecurityConfig.class);
            });
  }
}

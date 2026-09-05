/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractIT;
import io.camunda.optimize.CcsmOidcTestFixture;
import io.camunda.optimize.ExtraConfigurationConfigImportIT;
import io.camunda.optimize.rest.security.ccsm.CCSMSecurityConfigurerAdapter;
import io.camunda.optimize.rest.security.csl.OptimizeCamundaSecurityConfig;
import io.camunda.optimize.test.optimize.HealthClient;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the real Optimize application, over real HTTP against a real Elasticsearch, with {@code
 * optimize.security.csl.enabled} pinned to {@code true} rather than left to default — this class
 * exists specifically so the CI matrix's {@code -Doptimize.security.csl.enabled=false} escape hatch
 * cannot silently neuter it (the {@code matchIfMissing} default itself is covered by the unit test
 * {@code CslSecurityChainSelectionTest}). This is the one thing the full {@code ccsm-test} suite
 * cannot currently prove: every class in it extends {@code AbstractCCSMIT}, which also embeds a
 * real Zeebe broker ({@code ZeebeExtension}), and that broker's presence in the same JVM breaks
 * CSL's boot for reasons tracked in camunda/camunda#60184.
 *
 * <p>Extends {@link AbstractIT} directly (not {@code AbstractCCSMIT}), the same base class {@code
 * AbstractBrokerlessZeebeCCSMIT} already uses to opt out of the embedded broker, so this test is
 * unaffected by #60184 and gives real IT-level coverage of the new default while that issue is
 * open.
 *
 * <p>Deliberately does NOT use {@code @TestPropertySource}: every {@code ccsm-test} IT in this
 * Surefire/Failsafe fork shares one fixed HTTP port (see {@code optimizeHttpPort} in {@code
 * optimize/backend/pom.xml}), and Spring Test caches a distinct {@code @TestPropertySource}
 * signature as its own long-lived {@code ApplicationContext} — two contexts both trying to bind
 * that same fixed port collide ({@code PortInUseException}) as soon as this class runs alongside
 * any other CCSM IT in the same fork, which local single-class test runs can't surface. Instead
 * {@link #startAndUseNewOptimizeInstance()} is overridden and invoked explicitly per test, the same
 * restart-with-custom-config mechanism {@link ExtraConfigurationConfigImportIT} already uses: it
 * closes whichever instance is currently bound to the shared port and starts a fresh one with CSL
 * forced on, and {@code EmbeddedOptimizeExtension#afterEach} swaps back to the default (non-CSL)
 * shared instance afterward, freeing the port for whatever runs next in the fork.
 */
@Tag("ccsm-test")
@ActiveProfiles(CCSM_PROFILE)
public class CslDefaultEnabledIT extends AbstractIT {

  private final HealthClient healthClient =
      new HealthClient(() -> embeddedOptimizeExtension.getRequestExecutor());

  @Override
  protected void startAndUseNewOptimizeInstance() {
    final Map<String, String> properties =
        new HashMap<>(CcsmOidcTestFixture.STATIC_OIDC_PROPERTIES);
    properties.put("optimize.security.csl.enabled", "true");
    startAndUseNewOptimizeInstance(properties, CCSM_PROFILE);
  }

  @BeforeEach
  void bootWithCslEnabled() {
    startAndUseNewOptimizeInstance();
  }

  @Test
  void shouldActivateTheCslChainByDefault() {
    // given the app was restarted with optimize.security.csl.enabled=true
    // when the security configuration beans are resolved
    // then the CSL security config is active and the legacy CCSM adapter has backed off
    assertThat(embeddedOptimizeExtension.getBean(OptimizeCamundaSecurityConfig.class)).isNotNull();
    assertThat(
            embeddedOptimizeExtension
                .getApplicationContext()
                .getBeanNamesForType(CCSMSecurityConfigurerAdapter.class))
        .isEmpty();
  }

  @Test
  void shouldServeTheReadinessEndpointWithoutAuthentication() {
    // given the app was restarted with optimize.security.csl.enabled=true
    // when an unauthenticated request reaches the readiness endpoint
    // then it is served without requiring a session, exactly as under the legacy stack
    assertThat(healthClient.getReadiness().getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  void shouldRejectAnUnauthenticatedRequestToAProtectedEndpoint() {
    // given the app was restarted with optimize.security.csl.enabled=true
    // when an unauthenticated request reaches a protected API path
    final Response response =
        embeddedOptimizeExtension
            .getRequestExecutor()
            .getWebTarget()
            .path("report")
            .request()
            .get();

    // then the CSL chain rejects it rather than serving it or hanging on a cross-origin redirect
    assertThat(response.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
  }
}

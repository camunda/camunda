/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes cluster-level configuration for the unified orchestration-cluster webapp.
 *
 * <p>This endpoint is intentionally kept at {@code /internal/cluster-configuration} rather than the
 * V2 path {@code /v2/system/configuration} (which already exists for job metrics). Relocating to
 * the V2 path requires API governance approval from CamundaX; that is tracked as a pending decision
 * in the BFF unification SKILL.md §4.
 *
 * <p><strong>Multi-engine forward-compatibility note:</strong> This endpoint is cluster-scoped, not
 * tenant-scoped. When {@code TenantPathFilter} lands it must add {@code
 * /internal/cluster-configuration} to its cluster-scoped pass-through list alongside {@code
 * /v2/cluster/**} and {@code /v2/license}. The {@code contextPath} field in the response is the
 * deployment-level servlet context path only — the multi-engine tenant prefix is delivered
 * exclusively via {@code <base href>} in the rendered shell (see BFF unification SKILL.md §9,
 * 2026-05-01 entry).
 *
 * <p><strong>Auth posture:</strong> The endpoint is reachable unauthenticated (permitted in {@code
 * WebSecurityConfig}) because the FE shell needs it at bootstrap time, before it knows whether
 * login should be delegated.
 *
 * <p><strong>Caching:</strong> {@code Cache-Control: no-store}. Values are stable across a
 * deployment lifetime but the cost of a stale response (FE makes auth-flow decisions on {@code
 * isLoginDelegated}) outweighs the caching benefit.
 */
@RestController
@ConditionalOnWebappUiEnabled("tmp-webapp")
public class ClusterConfigurationController {

  static final String PATH = "/internal/cluster-configuration";

  private final ActiveComponentsResolver activeComponentsResolver;
  private final SecurityConfiguration securityConfiguration;
  private final ServletContext servletContext;
  private final boolean enterprise;
  private final boolean loginDelegated;
  private final DataSize maxRequestSize;
  private final String cloudStage;
  private final String cloudMixpanelToken;
  private final String cloudMixpanelApiHost;

  public ClusterConfigurationController(
      final ActiveComponentsResolver activeComponentsResolver,
      final SecurityConfiguration securityConfiguration,
      final ServletContext servletContext,
      @Value("${camunda.webapp.enterprise:false}") final boolean enterprise,
      @Value("${camunda.webapps.login-delegated:false}") final boolean loginDelegated,
      @Value("${spring.servlet.multipart.max-request-size:4MB}") final DataSize maxRequestSize,
      @Value("${camunda.webapp.cloud.stage:#{null}}") final String cloudStage,
      @Value("${camunda.webapp.cloud.mixpanel-token:#{null}}") final String cloudMixpanelToken,
      @Value("${camunda.webapp.cloud.mixpanel-api-host:#{null}}")
          final String cloudMixpanelApiHost) {
    this.activeComponentsResolver = activeComponentsResolver;
    this.securityConfiguration = securityConfiguration;
    this.servletContext = servletContext;
    this.enterprise = enterprise;
    this.loginDelegated = loginDelegated;
    this.maxRequestSize = maxRequestSize;
    this.cloudStage = cloudStage;
    this.cloudMixpanelToken = cloudMixpanelToken;
    this.cloudMixpanelApiHost = cloudMixpanelApiHost;
  }

  @GetMapping(PATH)
  public ResponseEntity<ClusterConfiguration> getClusterConfiguration() {
    final var saas = securityConfiguration.getSaas();
    final var cloud =
        new ClusterConfiguration.Cloud(
            saas.getOrganizationId(),
            saas.getClusterId(),
            cloudStage,
            cloudMixpanelToken,
            cloudMixpanelApiHost);

    final var config =
        new ClusterConfiguration(
            activeComponentsResolver.resolve(),
            enterprise,
            securityConfiguration.getMultiTenancy().isChecksEnabled(),
            /* canLogout= */ saas.getClusterId() == null,
            loginDelegated,
            servletContext.getContextPath(),
            maxRequestSize.toBytes(),
            cloud);

    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(config);
  }
}

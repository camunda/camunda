/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import java.util.List;

/**
 * Response DTO for {@code GET /internal/cluster-configuration}.
 *
 * <p>Public fields are used intentionally: Jackson serializes them directly by their declared name,
 * avoiding the JavaBeans {@code is}-prefix stripping that would otherwise misname boolean fields
 * (e.g., turning {@code isEnterprise} into {@code enterprise} in the JSON output).
 *
 * <p>This shape is proposed as a strawman for M1 and will be refined based on Vinicius and
 * Patrick's review. Fields that are pending a unified configuration source are annotated with
 * inline comments. Operate-specific fields (e.g., {@code tasklistUrl}, {@code
 * resourcePermissionsEnabled}) are deferred to M3/M4.
 */
public class ClusterConfiguration {

  /** Names of the webapp components whose UI is enabled in this deployment. */
  public final List<String> activeComponents;

  /**
   * Whether this is an enterprise deployment. Reads {@code camunda.webapp.enterprise}; defaults to
   * {@code false}. TODO(M2): map to whatever unified enterprise property the deployment uses —
   * today's operators set {@code camunda.tasklist.enterprise} or {@code
   * camunda.operate.enterprise}; those keys are not read here.
   */
  public final boolean isEnterprise;

  /** Whether multi-tenancy checks are enforced (from {@code SecurityConfiguration}). */
  public final boolean isMultiTenancyEnabled;

  /**
   * Whether the user can explicitly log out. {@code false} in SaaS deployments where logout is
   * handled by the identity provider. Derived from {@code SecurityConfiguration.getSaas()}.
   */
  public final boolean canLogout;

  /**
   * Whether login is delegated to an OIDC provider (from {@code camunda.webapps.login-delegated}).
   */
  public final boolean isLoginDelegated;

  /**
   * Deployment-level servlet context path (e.g., {@code ""} or {@code "/camunda"}). Never
   * tenant-prefixed — the multi-engine tenant prefix is delivered via {@code <base href>} in the
   * rendered shell, not via this endpoint.
   */
  public final String contextPath;

  /**
   * Maximum HTTP request size in bytes (from {@code spring.servlet.multipart.max-request-size}).
   */
  public final long maxRequestSize;

  /**
   * SaaS / cloud deployment coordinates. All fields are {@code null} in self-managed deployments.
   */
  public final Cloud cloud;

  public ClusterConfiguration(
      final List<String> activeComponents,
      final boolean isEnterprise,
      final boolean isMultiTenancyEnabled,
      final boolean canLogout,
      final boolean isLoginDelegated,
      final String contextPath,
      final long maxRequestSize,
      final Cloud cloud) {
    this.activeComponents = activeComponents;
    this.isEnterprise = isEnterprise;
    this.isMultiTenancyEnabled = isMultiTenancyEnabled;
    this.canLogout = canLogout;
    this.isLoginDelegated = isLoginDelegated;
    this.contextPath = contextPath;
    this.maxRequestSize = maxRequestSize;
    this.cloud = cloud;
  }

  /**
   * SaaS / cloud deployment coordinates. Fields are sourced from {@code SecurityConfiguration}
   * where available ({@code organizationId}, {@code clusterId}). The remaining fields ({@code
   * stage}, {@code mixpanelToken}, {@code mixpanelAPIHost}) read from {@code
   * camunda.webapp.cloud.*} and are pending a §4 decision on Mixpanel handling.
   */
  public static class Cloud {

    public final String organizationId;
    public final String clusterId;

    /**
     * Deployment stage (e.g., {@code prod}, {@code dev}). Reads {@code camunda.webapp.cloud.stage};
     * {@code null} when unset.
     */
    public final String stage;

    /**
     * Mixpanel project token. Reads {@code camunda.webapp.cloud.mixpanel-token}; {@code null} when
     * unset. Pending §4 decision: Mixpanel and other SaaS-only analytics config may move to a
     * separate internal endpoint.
     */
    public final String mixpanelToken;

    /**
     * Mixpanel API host override. Reads {@code camunda.webapp.cloud.mixpanel-api-host}; {@code
     * null} when unset.
     */
    public final String mixpanelAPIHost;

    public Cloud(
        final String organizationId,
        final String clusterId,
        final String stage,
        final String mixpanelToken,
        final String mixpanelAPIHost) {
      this.organizationId = organizationId;
      this.clusterId = clusterId;
      this.stage = stage;
      this.mixpanelToken = mixpanelToken;
      this.mixpanelAPIHost = mixpanelAPIHost;
    }
  }
}

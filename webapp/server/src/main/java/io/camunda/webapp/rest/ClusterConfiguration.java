/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response DTO for {@code GET /internal/cluster-configuration}.
 *
 * @param activeComponents names of the webapp components whose UI is enabled in this deployment
 * @param isEnterprise whether this is an enterprise deployment. Reads {@code
 *     camunda.webapp.enterprise}; defaults to {@code false}.
 * @param isMultiTenancyEnabled whether multi-tenancy checks are enforced (from {@code
 *     SecurityConfiguration})
 * @param canLogout whether the user can explicitly log out. {@code false} in SaaS deployments where
 *     logout is handled by the identity provider. Derived from {@code
 *     SecurityConfiguration.getSaas()}.
 * @param isLoginDelegated whether login is delegated to an OIDC provider (from {@code
 *     camunda.webapps.login-delegated})
 * @param contextPath deployment-level servlet context path (e.g., {@code ""} or {@code "/camunda"})
 * @param maxRequestSize maximum HTTP request size in bytes (from {@code
 *     spring.servlet.multipart.max-request-size})
 * @param cloud SaaS / cloud deployment coordinates. All fields are {@code null} in self-managed
 *     deployments.
 */
public record ClusterConfiguration(
    List<String> activeComponents,
    @JsonProperty("isEnterprise") boolean isEnterprise,
    @JsonProperty("isMultiTenancyEnabled") boolean isMultiTenancyEnabled,
    boolean canLogout,
    @JsonProperty("isLoginDelegated") boolean isLoginDelegated,
    String contextPath,
    long maxRequestSize,
    Cloud cloud) {

  /**
   * @param organizationId SaaS organization ID; {@code null} in self-managed deployments
   * @param clusterId SaaS cluster ID; {@code null} in self-managed deployments
   * @param stage deployment stage (e.g., {@code prod}, {@code dev}). Reads {@code
   *     camunda.webapp.cloud.stage}; {@code null} when unset.
   * @param mixpanelToken Mixpanel project token. Reads {@code camunda.webapp.cloud.mixpanel-token};
   *     {@code null} when unset.
   * @param mixpanelAPIHost Mixpanel API host override. Reads {@code
   *     camunda.webapp.cloud.mixpanel-api-host}; {@code null} when unset.
   */
  public record Cloud(
      String organizationId,
      String clusterId,
      String stage,
      String mixpanelToken,
      String mixpanelAPIHost) {}
}

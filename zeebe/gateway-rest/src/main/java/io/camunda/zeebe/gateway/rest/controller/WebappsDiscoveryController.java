/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.config.WebappsDiscoveryProperties;
import io.camunda.zeebe.gateway.rest.config.WebappsDiscoveryProperties.Webapp;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Publishes where this setup's webapps (Operate, Tasklist) live, so client applications (e.g. the
 * Camunda Modeler) can link to them instead of asking users to configure each UI URL separately
 * (camunda/camunda#46649). The contract is shared with the modeler: {@code GET
 * ${BASE_URL}/.well-known/camunda/webapps} returns {@code {"operateUrl": ..., "tasklistUrl": ...}},
 * omitting webapps that are not part of the setup.
 *
 * <p>The endpoint is unauthenticated via {@code SecurityPaths.UNPROTECTED_PATHS} — a chain without
 * any authentication filter — so discovery works before a client knows how (or whether) to
 * authenticate, and stray credentials cannot turn it into a 401.
 *
 * <p>The cluster cannot detect where frontends are exposed (they may sit behind a different
 * ingress), so operators announce split deployments explicitly via {@code
 * camunda.webapps.<app>.url}. Without an explicit URL, a webapp with its UI enabled is announced
 * under its default path, relative to the API — the layout of the default Helm chart and c8run.
 *
 * <p>The fallback deliberately returns a root-relative path rather than an absolute URL derived
 * from the servlet request: behind a TLS-terminating proxy the request reaches the app as plain
 * HTTP with a rewritten Host, so the derived absolute URL would advertise the wrong scheme and
 * port. Relative URLs resolve against the origin the client used for the API call, which is correct
 * by construction. Deployments where the webapps live on a different origin than the API must set
 * {@code camunda.webapps.<app>.url} explicitly (the Helm chart derives these from its ingress
 * values).
 */
@CamundaRestController
@ClusterScoped
@EnableConfigurationProperties(WebappsDiscoveryProperties.class)
@RequestMapping("/.well-known/camunda")
public class WebappsDiscoveryController {

  private static final String OPERATE_PATH = "/operate";
  private static final String TASKLIST_PATH = "/tasklist";

  private final WebappsDiscoveryProperties properties;
  private final Environment environment;

  public WebappsDiscoveryController(
      final WebappsDiscoveryProperties properties, final Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @CamundaGetMapping(path = "/webapps")
  public WebappsDiscoveryResponse getWebapps() {
    return new WebappsDiscoveryResponse(
        resolveUrl(properties.getOperate(), "operate", OPERATE_PATH),
        resolveUrl(properties.getTasklist(), "tasklist", TASKLIST_PATH));
  }

  private @Nullable String resolveUrl(
      final Webapp webapp, final String webappName, final String defaultPath) {
    if (webapp.getUrl() != null && !webapp.getUrl().isBlank()) {
      return webapp.getUrl();
    }
    // Mirror WebappsHelper: a webapp is served only when both the unified key and the legacy
    // per-app kill-switch (camunda.<app>.webappEnabled) allow it.
    final var legacyWebappEnabled =
        environment.getProperty("camunda." + webappName + ".webappEnabled", Boolean.class, true);
    if (!webapp.isEnabled() || !webapp.isUiEnabled() || !legacyWebappEnabled) {
      return null;
    }
    return defaultPath;
  }
}

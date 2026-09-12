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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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
 * under its default path on the same origin as the API, derived from the current request — the
 * layout of the default Helm chart and c8run.
 */
@CamundaRestController
@ClusterScoped
@EnableConfigurationProperties(WebappsDiscoveryProperties.class)
@RequestMapping("/.well-known/camunda")
public class WebappsDiscoveryController {

  private static final String OPERATE_PATH = "/operate";
  private static final String TASKLIST_PATH = "/tasklist";

  private final WebappsDiscoveryProperties properties;

  public WebappsDiscoveryController(final WebappsDiscoveryProperties properties) {
    this.properties = properties;
  }

  @CamundaGetMapping(path = "/webapps")
  public WebappsDiscoveryResponse getWebapps() {
    return new WebappsDiscoveryResponse(
        resolveUrl(properties.getOperate(), OPERATE_PATH),
        resolveUrl(properties.getTasklist(), TASKLIST_PATH));
  }

  private static @Nullable String resolveUrl(final Webapp webapp, final String defaultPath) {
    if (webapp.getUrl() != null && !webapp.getUrl().isBlank()) {
      return webapp.getUrl();
    }
    if (!webapp.isEnabled() || !webapp.isUiEnabled()) {
      return null;
    }
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path(defaultPath)
        .build()
        .toUriString();
  }
}

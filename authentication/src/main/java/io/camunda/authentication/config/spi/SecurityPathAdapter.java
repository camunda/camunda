/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.env.Environment;

/**
 * Host-supplied {@link SecurityPathPort} declaring the path patterns OC's filter chains operate on.
 * The configuration-independent sets live in {@link SecurityPaths}; the ones kept here are either
 * gated on configuration or read only through this port.
 *
 * <p>Construct only via {@link #fromEnvironment(Environment)}, so {@link #webappPaths()} always
 * reports the gate as configuration resolves it rather than as a caller asserts it.
 */
public final class SecurityPathAdapter implements SecurityPathPort {

  private static final Set<String> WEBAPP_PATHS =
      Set.of(
          "/login/**",
          "/logout",
          "/admin/**",
          "/operate/**",
          "/tasklist/**",
          "/assets/**",
          "/custom.css",
          "/favicon.ico",
          "/",
          "/sso-callback/**",
          "/oauth2/authorization/**",
          "/post-logout",
          "/processes",
          "/processes/*",
          "/{regex:[\\d]+}",
          "/processes/*/start",
          "/new/*",
          "/decisions",
          "/decisions/*",
          "/instances",
          "/instances/*",
          "/default-ui.css",
          "/swagger/**",
          "/swagger-ui/**",
          "/v3/api-docs/**");

  private static final Set<String> UNAUTHENTICATED_WEBAPP_PATHS =
      Set.of(
          "/post-logout",
          "/default-ui.css",
          "/operate/assets/**",
          "/operate/client-config.js",
          "/operate/custom.css",
          "/operate/favicon.ico",
          "/admin/assets/**",
          "/admin/favicon.ico",
          "/assets/**",
          "/custom.css",
          "/favicon.ico",
          "/swagger/**",
          "/swagger-ui/**",
          "/v3/api-docs/**");

  // Single source of truth for the web component names; see WebAppProviderAdapter#WEB_APPS.
  private static final Set<String> WEB_COMPONENT_NAMES = WebAppProviderAdapter.WEB_APPS;

  private static final Set<String> ADMIN_FILTER_BYPASS_PATHS =
      Set.of(
          "/login",
          "/logout",
          "/sso-callback",
          "/post-logout",
          "/admin/setup",
          // Setup-page assets (CSS, JS modules) must load before any admin user is provisioned;
          // without this prefix the filter redirects asset requests to /admin/setup, the SPA gets
          // text/html back for every <script>/<link> tag, and the browser refuses to evaluate the
          // setup page with MIME-type errors. Mirrors the explicit ASSETS_PATH bypass the
          // pre-CSL host AdminUserCheckFilter carried.
          "/admin/assets");

  private final boolean webappEnabled;

  private SecurityPathAdapter(final boolean webappEnabled) {
    this.webappEnabled = webappEnabled;
  }

  /**
   * Reads {@code webapp-enabled} with CSL's own property key and default, so this gate cannot drift
   * from the cluster webapp chains' conditions, which read the same key from the same {@link
   * Environment}.
   *
   * <p>The only way to build the adapter, so the gate is always resolved from configuration rather
   * than asserted by the caller. Note an {@link Environment} without the property resolves CSL's
   * default of enabled — a test that means to exercise a webapp-disabled cluster must set the
   * property, not merely pass a bare environment.
   */
  public static SecurityPathAdapter fromEnvironment(final Environment environment) {
    return new SecurityPathAdapter(
        environment.getProperty(
            CamundaSecurityFilterChainConstants.WEBAPP_ENABLED_PROPERTY,
            Boolean.class,
            AuthenticationConfiguration.DEFAULT_WEBAPP_ENABLED));
  }

  @Override
  public Set<String> apiPaths() {
    return SecurityPaths.API_PATHS;
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    return SecurityPaths.UNPROTECTED_API_PATHS;
  }

  @Override
  public Set<String> unprotectedPaths() {
    return SecurityPaths.UNPROTECTED_PATHS;
  }

  /**
   * The webapp path patterns, or an empty set when the webapp is disabled — how a host tells CSL it
   * serves no webapp, so it builds an inert chain instead. The scoped per-tenant chains need this
   * because, unlike the cluster chains, they are not gated on {@code webapp-enabled} themselves.
   */
  @Override
  public Set<String> webappPaths() {
    return webappEnabled ? WEBAPP_PATHS : Set.of();
  }

  @Override
  public Set<String> webComponentNames() {
    return WEB_COMPONENT_NAMES;
  }

  @Override
  public Set<String> unauthenticatedWebappPaths() {
    return UNAUTHENTICATED_WEBAPP_PATHS;
  }

  @Override
  public Set<String> adminFilterBypassPaths() {
    return ADMIN_FILTER_BYPASS_PATHS;
  }

  @Override
  public Optional<String> postLogoutRedirectPath() {
    return Optional.of("/post-logout");
  }

  // staticResourceSuffixes() inherits the SPI default which already matches OC's source set.
}

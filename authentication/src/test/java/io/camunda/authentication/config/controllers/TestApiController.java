/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.controllers;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.context.holder.HttpSessionBasedAuthenticationHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class TestApiController {

  public static final String DEFAULT_RESPONSE = "I am dummy endpoint";
  public static final String DUMMY_OPERATE_INTERNAL_API_ENDPOINT = "/api/foo";
  public static final String DUMMY_V1_API_ENDPOINT = "/v1/foo";
  public static final String DUMMY_V2_API_ENDPOINT = "/v2/foo";
  public static final String DUMMY_V2_API_AUTH_ENDPOINT = "/v2/auth";
  public static final String DUMMY_WEBAPP_ENDPOINT = "/operate/decisions";
  public static final String DUMMY_UNPROTECTED_ENDPOINT = "/favicon.ico";
  public static final String DUMMY_UNHANDLED_ENDPOINT = "/non-existent-endpoint";

  /**
   * Cluster-admin dummy endpoint at the real {@code /cluster/v2/topology} path, so the
   * cluster-admin chain is exercised here (its real controller is in another module, off this
   * slice's classpath).
   */
  public static final String DUMMY_CLUSTER_ADMIN_ENDPOINT = "/cluster/v2/topology";

  /**
   * Isolated, additive endpoint used only by {@code SessionAuthenticationRefreshTest} to force a
   * real, Spring-Session-backed session to exist before the request under test runs. With CSL
   * ADR-0031's per-chain {@code SessionRepositoryFilter}, nothing creates a session for a request
   * authenticated via {@code SecurityMockMvcRequestPostProcessors.authentication(...)} (unlike a
   * real login, no success handler calls {@code SecurityContextRepository#saveContext}), and {@code
   * HttpSessionBasedAuthenticationHolder} itself only ever reads an existing session ({@code
   * getSession(false)}) — it never creates one. Kept separate from the other dummy endpoints so
   * their behavior for every other test is unaffected.
   */
  public static final String DUMMY_SESSION_WARMUP_ENDPOINT = "/operate/session-warmup";

  /**
   * Exposes the session's {@code HttpSessionBasedAuthenticationHolder.LAST_REFRESH_ATTR} value (if
   * any) as a response header, so {@code SessionAuthenticationRefreshTest} can observe the
   * server-side refresh timestamp without reading it back off a client-held session object — which
   * no longer reflects reality now that a real {@code SessionRepositoryFilter} resolves sessions by
   * cookie rather than by the request's raw session reference (CSL ADR-0031).
   */
  public static final String LAST_AUTH_REFRESH_HEADER = "X-Test-Last-Auth-Refresh";

  private final CamundaAuthenticationProvider testAuthenticationProvider;

  public TestApiController(
      @Autowired(required = false) final CamundaAuthenticationProvider testAuthenticationProvider) {
    this.testAuthenticationProvider = testAuthenticationProvider;
  }

  /**
   * Resolves the current {@link io.camunda.security.api.model.CamundaAuthentication}, which is what
   * actually drives {@link HttpSessionBasedAuthenticationHolder}'s refresh-interval bookkeeping,
   * and exposes the resulting session timestamp as a response header for the test to read.
   */
  private void resolveAuthenticationAndExposeLastRefresh(
      final HttpServletRequest request, final HttpServletResponse response) {
    if (testAuthenticationProvider != null) {
      testAuthenticationProvider.getCamundaAuthentication();
    }
    final var session = request.getSession(false);
    final Object lastRefresh =
        session == null
            ? null
            : session.getAttribute(HttpSessionBasedAuthenticationHolder.LAST_REFRESH_ATTR);
    if (lastRefresh != null) {
      response.setHeader(LAST_AUTH_REFRESH_HEADER, lastRefresh.toString());
    }
  }

  @RequestMapping(DUMMY_OPERATE_INTERNAL_API_ENDPOINT)
  public @ResponseBody String dummyOperateInternalApiEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_V1_API_ENDPOINT)
  public @ResponseBody String dummyV1ApiEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_V2_API_ENDPOINT)
  public @ResponseBody String dummyV2ApiEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_V2_API_AUTH_ENDPOINT)
  public @ResponseBody String dummyV2ApiAuthEndpoint(
      final HttpServletRequest request, final HttpServletResponse response) {
    resolveAuthenticationAndExposeLastRefresh(request, response);
    return testAuthenticationProvider != null
            && testAuthenticationProvider.getCamundaAuthentication() != null
        ? testAuthenticationProvider.getCamundaAuthentication().authenticatedUsername()
        : "None";
  }

  @RequestMapping(DUMMY_WEBAPP_ENDPOINT)
  public @ResponseBody String dummyWebappEndpoint(
      final HttpServletRequest request, final HttpServletResponse response) {
    resolveAuthenticationAndExposeLastRefresh(request, response);
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_UNPROTECTED_ENDPOINT)
  public @ResponseBody String dummyUnprotectedEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @RequestMapping(DUMMY_SESSION_WARMUP_ENDPOINT)
  public @ResponseBody String dummySessionWarmupEndpoint(final HttpServletRequest request) {
    request.getSession(true);
    return DEFAULT_RESPONSE;
  }

  @PostMapping(DUMMY_V2_API_ENDPOINT)
  public @ResponseBody String dummyV2ApiPostEndpoint() {
    return DEFAULT_RESPONSE;
  }

  @GetMapping(DUMMY_CLUSTER_ADMIN_ENDPOINT)
  public @ResponseBody String dummyClusterAdminEndpoint() {
    return DEFAULT_RESPONSE;
  }
}

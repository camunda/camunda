/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static io.camunda.security.spring.security.CamundaSecurityFilterChainConstants.ORDER_API;

import io.camunda.authentication.clusteradmin.ClusterApiSettings.ClusterAdminAuthenticationMethod;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.handler.AuthFailureHandler;
import io.camunda.security.spring.security.SecurityFilterChainSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Unauthenticated chain for the cluster-admin API, active when {@code
 * camunda.security.cluster-admin.authentication.method=none}.
 *
 * <p>Only allowed together with a dedicated cluster API port ({@code camunda.api.cluster.port}) and
 * an explicit bind address ({@code camunda.api.cluster.address}): access control is then network
 * reachability of that address and port, as for the actuator port (ADR management/002 D1). Requests
 * issued by a browser on behalf of a web page are refused (see {@link #BROWSER_REQUEST}). On the
 * shared REST port, disabling authentication would expose cluster-wide destructive operations to
 * every caller that can reach the public API, so startup fails instead.
 *
 * <p>Every request is treated as a cluster admin: the anonymous principal carries {@link
 * ClusterAdminBasicSecurityConfiguration#CLUSTER_ADMIN_AUTHORITY}, so {@link
 * ClusterAdminAuthenticationConverter} maps it like any other cluster-admin principal, without a
 * membership lookup.
 */
@Configuration
@ConditionalOnClusterAdminAuthenticationMethod(ClusterAdminAuthenticationMethod.NONE)
public class ClusterAdminNoAuthSecurityConfiguration {

  static final String ANONYMOUS_CLUSTER_ADMIN = "anonymous-cluster-admin";

  /**
   * Matches requests a browser sends on behalf of a web page. Without credentials, the only thing
   * keeping a page an operator happens to open from triggering cluster operations (e.g. a no-cors
   * {@code POST /cluster/v2/exporting/pause}, or via DNS rebinding) is refusing them: browsers set
   * {@code Origin} on cross-origin and non-GET requests and {@code Sec-Fetch-Site} on every fetch,
   * while operator tooling (curl, scripts, the Java client) sends neither, or {@code
   * Sec-Fetch-Site: none} for a URL typed into the address bar.
   */
  static final RequestMatcher BROWSER_REQUEST =
      request -> {
        final String fetchSite = request.getHeader("Sec-Fetch-Site");
        return request.getHeader("Origin") != null
            || (fetchSite != null && !"none".equals(fetchSite));
      };

  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterAdminNoAuthSecurityConfiguration.class);

  @Bean(ClusterApiSettings.NO_AUTH_CHAIN_BEAN)
  @Order(ORDER_API)
  public SecurityFilterChain clusterAdminNoAuthSecurityFilterChain(
      final HttpSecurity http,
      final Environment environment,
      final CamundaSecurityLibraryProperties properties)
      throws Exception {
    // load() refuses NONE without a dedicated port and an explicit bind address
    final ClusterApiSettings settings = ClusterApiSettings.load(environment);
    LOG.warn(
        "Cluster-admin authentication is disabled: {} on {}:{} is reachable without "
            + "credentials. Restrict network access to this address and port.",
        ClusterApiSettings.CLUSTER_API_PATTERN,
        settings.address(),
        settings.port());

    http.securityMatcher(settings.dedicatedPortMatcher())
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(BROWSER_REQUEST).denyAll().anyRequest().permitAll())
        .anonymous(
            anonymous ->
                anonymous
                    .principal(ANONYMOUS_CLUSTER_ADMIN)
                    .authorities(ClusterAdminBasicSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY));
    return statelessWithoutLogin(http, properties);
  }

  /**
   * Denies the cluster API on every other port. The port gate already answers 404 there; this keeps
   * the unauthenticated access confined to the dedicated port even without the gate.
   */
  @Bean
  @Order(ORDER_API)
  public SecurityFilterChain clusterAdminNoAuthOtherPortsSecurityFilterChain(
      final HttpSecurity http,
      final Environment environment,
      final AuthFailureHandler authFailureHandler,
      final CamundaSecurityLibraryProperties properties)
      throws Exception {
    http.securityMatcher(ClusterApiSettings.load(environment).otherPortsMatcher())
        .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
        .anonymous(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            eh ->
                eh.authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler));
    return statelessWithoutLogin(http, properties);
  }

  private static SecurityFilterChain statelessWithoutLogin(
      final HttpSecurity http, final CamundaSecurityLibraryProperties properties) throws Exception {
    http.httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(
            sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .csrf(AbstractHttpConfigurer::disable);
    SecurityFilterChainSupport.setupSecureHeaders(http, properties.getHttpHeaders());
    return http.build();
  }
}

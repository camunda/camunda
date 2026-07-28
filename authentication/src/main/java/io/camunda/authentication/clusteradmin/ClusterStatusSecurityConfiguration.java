/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static io.camunda.security.spring.security.CamundaSecurityFilterChainConstants.ORDER_UNPROTECTED;

import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.security.SecurityFilterChainSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Unauthenticated chain for the single public endpoint in the otherwise cluster-admin-protected
 * {@code /cluster/v2} namespace: {@code GET /cluster/v2/status}. Per ADR 002 D1 (exception noted
 * for ADR 001 D4), the cluster status must be pollable by monitoring without credentials, and it
 * exposes nothing sensitive — only an aggregated status, never a physical tenant id or count.
 *
 * <p><strong>Why a separate chain rather than {@code permitAll()} inside the cluster-admin
 * chains:</strong> {@code permitAll()} authorizes the request but does not stop {@link
 * ClusterAdminBasicSecurityConfiguration}'s {@code httpBasic} or {@link
 * ClusterAdminOidcSecurityConfiguration}'s {@code oauth2ResourceServer} from processing a present
 * {@code Authorization} header and rejecting it. Callers migrating here from {@code /v2/status} —
 * notably {@code CamundaClient}, which sends its configured credentials on every request — carry
 * regular API credentials, which are unknown to the isolated cluster-admin user store. They would
 * get 401 instead of a status. This chain installs no authentication filter at all, so the {@code
 * Authorization} header is never inspected.
 *
 * <p>Registered at {@link
 * io.camunda.security.spring.security.CamundaSecurityFilterChainConstants#ORDER_UNPROTECTED}, ahead
 * of both cluster-admin chains at {@code ORDER_API}: {@code FilterChainProxy} dispatches to the
 * first chain whose matcher matches, so the cluster-admin chains never see this request and need no
 * change. The matcher is deliberately an exact path with no wildcard, so the rest of {@code
 * /cluster/v2/**} keeps falling through to them. It is not restricted to {@code GET} either, so an
 * unsupported method on this path answers 405 from the MVC layer rather than a misleading 401 from
 * the cluster-admin chain.
 *
 * <p>Registered unconditionally: unlike the cluster-admin chains, which are split by authentication
 * method, this one has no authentication to configure.
 */
@Configuration
public class ClusterStatusSecurityConfiguration {

  static final String CLUSTER_STATUS_PATTERN = "/cluster/v2/status";

  @Bean
  @Order(ORDER_UNPROTECTED)
  public SecurityFilterChain clusterStatusSecurityFilterChain(
      final HttpSecurity http, final CamundaSecurityLibraryProperties properties) throws Exception {
    http.securityMatcher(CLUSTER_STATUS_PATTERN)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        // Session-free by construction: a health check must never mint or read a session, and an
        // existing webapp session cookie must not influence the response.
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(sc -> sc.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .csrf(AbstractHttpConfigurer::disable);

    SecurityFilterChainSupport.setupSecureHeaders(http, properties.getHttpHeaders());

    return http.build();
  }
}

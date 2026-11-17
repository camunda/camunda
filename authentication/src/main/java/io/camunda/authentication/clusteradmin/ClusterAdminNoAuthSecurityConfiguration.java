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

/**
 * Unauthenticated chain for the cluster-admin API, active when {@code
 * camunda.security.cluster-admin.authentication.method=none}.
 *
 * <p>Only allowed together with a dedicated cluster API port ({@code camunda.api.cluster.port}):
 * access control is then network reachability of that port, as for the actuator port (ADR
 * management/002 D1). On the shared REST port, disabling authentication would expose cluster-wide
 * destructive operations to every caller that can reach the public API, so startup fails instead.
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

  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterAdminNoAuthSecurityConfiguration.class);

  @Bean
  @Order(ORDER_API)
  public SecurityFilterChain clusterAdminNoAuthSecurityFilterChain(
      final HttpSecurity http,
      final Environment environment,
      final CamundaSecurityLibraryProperties properties)
      throws Exception {
    final int port =
        ClusterApiSettings.dedicatedPort(environment)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "%s=none requires the cluster API on a dedicated port; set %s"
                            .formatted(
                                ClusterApiSettings.AUTHENTICATION_METHOD_PROPERTY,
                                ClusterApiSettings.PORT_PROPERTY)));
    LOG.warn(
        "Cluster-admin authentication is disabled: {} on port {} is reachable without "
            + "credentials. Restrict network access to this port.",
        ClusterApiSettings.CLUSTER_API_PATTERN,
        port);

    http.securityMatcher(ClusterApiSettings.clusterApiMatcher(environment))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .anonymous(
            anonymous ->
                anonymous
                    .principal(ANONYMOUS_CLUSTER_ADMIN)
                    .authorities(ClusterAdminBasicSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY))
        .httpBasic(AbstractHttpConfigurer::disable)
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

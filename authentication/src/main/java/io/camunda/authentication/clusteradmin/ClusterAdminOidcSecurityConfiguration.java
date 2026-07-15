/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static io.camunda.security.spring.security.CamundaSecurityFilterChainConstants.ORDER_WEBAPP_API;

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * OIDC security chain for the cluster-admin API under {@code /cluster/v2/**}, active only when the
 * deployment runs OIDC ({@code @ConditionalOnAuthenticationMethod(OIDC)}). Bearer tokens are
 * validated against the deployment's default OIDC provider (the shared {@link JwtDecoder}); a token
 * is granted access only if it matches a configured cluster-admin client id, group, or claim (see
 * {@link ClusterAdminJwtAuthenticationConverter}). See camunda/camunda#57708.
 *
 * <p>This chain and the Basic chain ({@link ClusterAdminSecurityConfiguration}) are mutually
 * exclusive by deployment method — only one is instantiated.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class ClusterAdminOidcSecurityConfiguration {

  private static final String CLUSTER_ADMIN_API_PATTERN = "/cluster/v2/**";

  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterAdminOidcSecurityConfiguration.class);

  @Bean
  @Order(ORDER_WEBAPP_API)
  public SecurityFilterChain clusterAdminOidcSecurityFilterChain(
      final HttpSecurity http,
      final Environment environment,
      final JwtDecoder jwtDecoder,
      final AuthFailureHandler authFailureHandler,
      final CamundaSecurityLibraryProperties properties)
      throws Exception {
    final OidcConfiguration oidc = properties.getAuthentication().getOidc();
    final ClusterAdminOidcProperties oidcProperties =
        ClusterAdminOidcProperties.loadAndValidate(
            environment, oidc.getClientIdClaim(), oidc.getGroupsClaim());
    LOG.info(
        "Loaded {} cluster-admin OIDC matcher(s) for {}",
        oidcProperties.clients().size()
            + oidcProperties.groups().size()
            + oidcProperties.claims().size(),
        CLUSTER_ADMIN_API_PATTERN);

    final var jwtAuthenticationConverter =
        new ClusterAdminJwtAuthenticationConverter(
            oidcProperties, oidc.getClientIdClaim(), oidc.getGroupsClaim());

    http.securityMatcher(CLUSTER_ADMIN_API_PATTERN)
        .authorizeHttpRequests(
            auth ->
                auth.anyRequest()
                    .hasAuthority(ClusterAdminSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                    .accessDeniedHandler(authFailureHandler))
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.NEVER))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        // Stateless bearer chain: no session, no CSRF surface, so disable it explicitly.
        .csrf(AbstractHttpConfigurer::disable);

    SecurityFilterChainSupport.setupSecureHeaders(http, properties.getHttpHeaders());

    return http.build();
  }
}

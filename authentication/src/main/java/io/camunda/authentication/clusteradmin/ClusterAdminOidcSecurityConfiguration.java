/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static io.camunda.security.spring.security.CamundaSecurityFilterChainConstants.ORDER_API;

import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.security.spring.handler.AuthFailureHandler;
import io.camunda.security.spring.security.SecurityFilterChainSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * OIDC security chain for the cluster-admin API under {@code /cluster/v2/**}, active only when the
 * deployment runs OIDC ({@code @ConditionalOnAuthenticationMethod(OIDC)}). Bearer tokens are
 * validated against the deployment's default OIDC provider (the shared {@link JwtDecoder}); a token
 * is granted access only if it matches a configured cluster-admin client id, group, or claim (see
 * {@link ClusterAdminJwtAuthenticationConverter}).
 *
 * <p>This chain and the Basic chain ({@link ClusterAdminSecurityConfiguration}) are mutually
 * exclusive by deployment method — only one is instantiated.
 *
 * <p><strong>Statelessness:</strong> the chain binds a request-scoped, session-free {@link
 * RequestAttributeSecurityContextRepository} explicitly and never creates a session, so an existing
 * webapp OIDC session cookie cannot authenticate {@code /cluster/v2/**} without a bearer token.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public class ClusterAdminOidcSecurityConfiguration {

  private static final String CLUSTER_ADMIN_API_PATTERN = "/cluster/v2/**";

  @Bean
  @Order(ORDER_API)
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
                    .authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        // Request-scoped, session-free context repository bound explicitly so a webapp OIDC session
        // cookie can't authenticate this bearer chain. Explicit is required: STATELESS installs one
        // only if none was set already (SessionManagementConfigurer's `if (repo == null)` guard).
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(
            sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .csrf(AbstractHttpConfigurer::disable);

    SecurityFilterChainSupport.setupSecureHeaders(http, properties.getHttpHeaders());

    return http.build();
  }
}

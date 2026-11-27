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
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.handler.AuthFailureHandler;
import io.camunda.security.spring.security.SecurityFilterChainSupport;
import org.springframework.beans.factory.ObjectProvider;
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
 * effective cluster-admin method is OIDC (see {@link ClusterApiSettings#load}). OIDC requires the
 * deployment itself to run OIDC, as the chain reuses its {@link JwtDecoder}. Bearer tokens are
 * validated against the deployment's default OIDC provider (the shared {@link JwtDecoder}); a token
 * is granted access only if it matches a configured cluster-admin client id, group, or claim (see
 * {@link ClusterAdminJwtAuthenticationConverter}).
 *
 * <p>This chain, the Basic chain ({@link ClusterAdminBasicSecurityConfiguration}) and the no-auth
 * chain ({@link ClusterAdminNoAuthSecurityConfiguration}) are mutually exclusive — only one is
 * instantiated.
 *
 * <p><strong>Statelessness:</strong> the chain binds a request-scoped, session-free {@link
 * RequestAttributeSecurityContextRepository} explicitly and never creates a session, so an existing
 * webapp OIDC session cookie cannot authenticate {@code /cluster/v2/**} without a bearer token.
 */
@Configuration
@ConditionalOnClusterAdminAuthenticationMethod(ClusterAdminAuthenticationMethod.OIDC)
public class ClusterAdminOidcSecurityConfiguration {

  @Bean(ClusterApiSettings.OIDC_CHAIN_BEAN)
  @Order(ORDER_API)
  public SecurityFilterChain clusterAdminOidcSecurityFilterChain(
      final HttpSecurity http,
      final Environment environment,
      final ObjectProvider<JwtDecoder> jwtDecoderProvider,
      final AuthFailureHandler authFailureHandler,
      final CamundaSecurityLibraryProperties properties)
      throws Exception {
    final JwtDecoder jwtDecoder = jwtDecoderProvider.getIfAvailable();
    if (jwtDecoder == null) {
      throw new IllegalStateException(
          "%s=oidc requires the deployment to use OIDC authentication"
              .formatted(ClusterApiSettings.AUTHENTICATION_METHOD_PROPERTY));
    }
    final OidcConfiguration oidc = properties.getAuthentication().getOidc();
    final ClusterAdminOidcProperties oidcProperties =
        ClusterAdminOidcProperties.loadAndValidate(
            environment, oidc.getClientIdClaim(), oidc.getGroupsClaim());

    final var jwtAuthenticationConverter =
        new ClusterAdminJwtAuthenticationConverter(
            oidcProperties, oidc.getClientIdClaim(), oidc.getGroupsClaim());

    // Path-only, on every port: see ClusterAdminBasicSecurityConfiguration.
    http.securityMatcher(ClusterApiSettings.load(environment).clusterApiMatcher())
        .authorizeHttpRequests(
            auth ->
                auth.anyRequest()
                    .hasAuthority(ClusterAdminBasicSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY))
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

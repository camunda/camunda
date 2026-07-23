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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Dedicated security chain for the cluster-admin API under {@code /cluster/v2/**}.
 *
 * <p>All paths under {@code /cluster/v2/**} require HTTP Basic authentication against the
 * statically configured cluster-admin users ({@code camunda.security.cluster-admin.basic.users}).
 * Registered only under the basic authentication method (the default)
 *
 * <p><strong>Isolation:</strong> keep the cluster-admin user store isolated as a plain in-memory
 * object behind a dedicated, parent-less {@link ProviderManager} for this chain. Do not expose it
 * as a {@code UserDetailsService} or {@code PasswordEncoder} bean. This avoids two problems:
 * accidentally suppressing CSL's global DB-backed auth beans, and allowing unknown in-memory
 * credentials to fall through to the global manager. Without this isolation, a DB-backed user could
 * authenticate on {@code /cluster/v2/**}.
 *
 * <p><strong>Statelessness:</strong> the chain binds a session-free {@link
 * NullSecurityContextRepository} explicitly and never creates a session, so an existing webapp
 * session cookie can never authenticate {@code /cluster/v2/**}.
 *
 * <p><strong>OIDC:</strong> handled by separate {@link ClusterAdminOidcSecurityConfiguration},
 * gated by {@code @ConditionalOnAuthenticationMethod(OIDC)}.
 *
 * <p>The Basic and OIDC chains are mutually exclusive by deployment method, so both can target
 * {@code /cluster/v2/**} without conflict.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
public class ClusterAdminSecurityConfiguration {

  /**
   * Marker authority assigned to every cluster-admin principal. The chain does not check it
   * directly, because authentication alone is enough in this all-or-nothing model. {@link
   * ClusterAdminAuthenticationConverter} uses it to identify these principals and skip the
   * DB-backed membership lookup.
   */
  public static final String CLUSTER_ADMIN_AUTHORITY = "ROLE_CLUSTER_ADMIN";

  static final String CLUSTER_ADMIN_API_PATTERN = "/cluster/v2/**";

  private static final Logger LOG =
      LoggerFactory.getLogger(ClusterAdminSecurityConfiguration.class);

  @Bean
  @Order(ORDER_API)
  public SecurityFilterChain clusterAdminSecurityFilterChain(
      final HttpSecurity http,
      final Environment environment,
      final PasswordEncoder passwordEncoder,
      final AuthFailureHandler authFailureHandler,
      final CamundaSecurityLibraryProperties properties)
      throws Exception {
    http.authenticationManager(clusterAdminAuthenticationManager(environment, passwordEncoder));

    http.securityMatcher(CLUSTER_ADMIN_API_PATTERN)
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .httpBasic(Customizer.withDefaults())
        .exceptionHandling(
            eh ->
                eh.authenticationEntryPoint(authFailureHandler)
                    .accessDeniedHandler(authFailureHandler))
        .cors(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        // Explicit session-free context repository so a webapp session cookie can't authenticate
        // this chain. Explicit is required: STATELESS installs one only if none was set already
        // (SessionManagementConfigurer's `if (repo == null)` guard).
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(sc -> sc.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .csrf(AbstractHttpConfigurer::disable);

    SecurityFilterChainSupport.setupSecureHeaders(http, properties.getHttpHeaders());

    return http.build();
  }

  /**
   * Builds the isolated authentication manager for this chain: the configured cluster-admin users
   * in in-memory store behind an explicit, parent-less {@link ProviderManager}. Parent-less (not
   * {@code http.getSharedObject(AuthenticationManagerBuilder.class)}) so credentials are checked
   * only against this store — otherwise unknown users would fall through to the global DB-backed
   * manager and a DB user could authenticate on {@code /cluster/v2/**}.
   */
  private AuthenticationManager clusterAdminAuthenticationManager(
      final Environment environment, final PasswordEncoder passwordEncoder) {
    final var users = ClusterAdminBasicAuthProperties.loadAndValidate(environment);

    // The shared PasswordEncoder bean is present because this chain and that bean share the same
    // @ConditionalOnAuthenticationMethod(BASIC) gate. It encodes the configured passwords below and
    // verifies them on every request, so encode and verify always agree.
    final var userDetailsManager = new InMemoryUserDetailsManager();
    for (final ClusterAdminUser user : users) {
      userDetailsManager.createUser(
          User.withUsername(user.name())
              .password(passwordEncoder.encode(user.password()))
              .authorities(CLUSTER_ADMIN_AUTHORITY)
              .build());
    }
    LOG.info(
        "Loaded {} cluster-admin basic-auth user(s) for {}",
        users.size(),
        CLUSTER_ADMIN_API_PATTERN);

    final var authenticationProvider = new DaoAuthenticationProvider(userDetailsManager);
    authenticationProvider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(authenticationProvider);
  }
}

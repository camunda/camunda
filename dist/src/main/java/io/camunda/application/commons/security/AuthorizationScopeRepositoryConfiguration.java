/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import io.camunda.security.impl.SearchAuthorizationScopeRepository;
import io.camunda.security.spring.CamundaSecurityAutoConfiguration;
import io.camunda.security.spring.authz.AuthorizationCheckerConfiguration;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the search-backed {@link AuthorizationScopeRepositoryPort} and ensures that the {@link
 * io.camunda.security.core.auth.AuthorizationChecker} bean is always available.
 *
 * <p>{@link AuthorizationCheckerConfiguration} is imported directly here rather than relying on the
 * CSL umbrella ({@link CamundaSecurityAutoConfiguration}) activated via
 * {@code @ImportAutoConfiguration} in {@code WebSecurityConfig}, because {@code WebSecurityConfig}
 * is not always active (e.g. in non-web or minimal application contexts). The {@link
 * io.camunda.security.core.auth.AuthorizationChecker} is however always required by the {@code
 * ServiceRegistry}, so it must be unconditionally available regardless of which application profile
 * is active.
 */
@NullMarked
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(AuthorizationCheckerConfiguration.class)
public class AuthorizationScopeRepositoryConfiguration {

  /**
   * Creates the {@link AuthorizationScopeRepositoryPort} backed by the search-layer {@link
   * AuthorizationReader}.
   */
  @Bean
  public AuthorizationScopeRepositoryPort authorizationScopeRepositoryPort(
      final AuthorizationReader authorizationReader) {
    return new SearchAuthorizationScopeRepository(authorizationReader);
  }
}

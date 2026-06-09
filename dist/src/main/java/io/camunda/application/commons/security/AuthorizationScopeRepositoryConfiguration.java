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
import io.camunda.security.spring.auth.AuthorizationCheckerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires the search-backed {@link AuthorizationScopeRepositoryPort} and imports CSL's {@link
 * AuthorizationCheckerConfiguration} so that an {@link
 * io.camunda.security.core.auth.AuthorizationChecker} bean is available to callers that depend on
 * it for authorization checks.
 */
@Configuration(proxyBeanMethods = false)
@Import(AuthorizationCheckerConfiguration.class)
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

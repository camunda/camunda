/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration.app.adapter;

import io.camunda.gatekeeper.spi.SecurityPathProvider;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Stub security path provider for integration tests. Declares minimal paths that match the test
 * controller endpoints. In production, each consuming application provides its own implementation
 * with application-specific paths.
 */
@Component
public final class StubSecurityPathProvider implements SecurityPathProvider {

  @Override
  public Set<String> apiPaths() {
    return Set.of("/v2/**");
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    return Set.of("/v2/license");
  }

  @Override
  public Set<String> unprotectedPaths() {
    return Set.of("/actuator/**", "/error");
  }

  @Override
  public Set<String> webappPaths() {
    return Set.of("/login/**", "/logout", "/oauth2/**", "/sso-callback", "/app/**");
  }

  @Override
  public Set<String> webComponentNames() {
    return Set.of();
  }
}

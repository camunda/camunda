/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.out.SecurityPathPort;
import java.util.Set;

/**
 * SPIKE (ADR-0036): Optimize's implementation of the CSL {@link SecurityPathPort}.
 *
 * <ul>
 *   <li>{@link #apiPaths()} = the bearer-only public API surface. Only these are claimed by the
 *       CSL OIDC API chain (bearer JWT). Everything else under {@code /api} is served by the
 *       catch-all webapp chain and authenticated from the session.
 *   <li>{@link #unprotectedApiPaths()} = the public subset of {@link #apiPaths()} (the subset
 *       contract is kept). Empty for Optimize: the public API and ingestion endpoints are all
 *       protected.
 *   <li>{@link #unprotectedPaths()} = every public path outside the bearer surface. This is the
 *       bucket the order-0 unprotected chain actually matches, so the public endpoints under
 *       {@code /api} (readyz, ui-configuration, localization, external) go here too, not in
 *       {@link #unprotectedApiPaths()}.
 *   <li>{@link #webappPaths()} = {@code /**}. The webapp chain is the catch-all; with the CSL
 *       API-before-webapp ordering it sorts below the bearer API chain.
 * </ul>
 */
public final class OptimizeSecurityPathAdapter implements SecurityPathPort {

  @Override
  public Set<String> apiPaths() {
    return Set.of("/api/public/**", "/api/ingestion/variable");
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    // Public subset of apiPaths(); none for Optimize (public API + ingestion are protected).
    return Set.of();
  }

  @Override
  public Set<String> unprotectedPaths() {
    // Every public path outside the bearer surface, including public endpoints under /api.
    return Set.of(
        "/api/readyz",
        "/api/ui-configuration",
        "/api/localization",
        "/api/external/**",
        "/external/**",
        "/static/**",
        "/*.ico",
        "/*.html",
        "/actuator/**");
  }

  @Override
  public Set<String> webappPaths() {
    // Catch-all; sorts below the bearer API chain via CSL's API-before-webapp ordering.
    return Set.of("/**");
  }

  @Override
  public Set<String> webComponentNames() {
    return Set.of("optimize");
  }
}

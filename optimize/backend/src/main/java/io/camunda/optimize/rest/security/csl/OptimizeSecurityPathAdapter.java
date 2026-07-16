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
 * SPIKE (ADR-0036): Optimize's implementation of the CSL {@link SecurityPathPort}. It maps
 * Optimize's endpoints onto the four CSL path categories.
 *
 * <p>The categorisation follows the design agreed in the spike:
 *
 * <ul>
 *   <li>{@link #apiPaths()} = the bearer-only public API surface. Only these are claimed by the
 *       CSL OIDC API chain (bearer JWT). Everything else under {@code /api} is served by the
 *       catch-all webapp chain and authenticated from the session.
 *   <li>{@link #unprotectedApiPaths()} = public paths under {@code /api} (readyz, ui-configuration,
 *       localization, the login callback and logout, the public share API). These need NOT be a
 *       subset of {@link #apiPaths()} (CSL Javadoc fixed in this direction).
 *   <li>{@link #unprotectedPaths()} = public non-API paths (embedded share UI under {@code
 *       /external}, static resources, actuator).
 *   <li>{@link #webappPaths()} = {@code /**}. The webapp chain is the catch-all for everything not
 *       claimed by a higher-priority chain. See {@link OptimizeWebappSecurityConfiguration} for the
 *       ordering constraint this implies.
 * </ul>
 */
public final class OptimizeSecurityPathAdapter implements SecurityPathPort {

  @Override
  public Set<String> apiPaths() {
    // Bearer-only API surface. Kept deliberately small: only the public REST API and the
    // variable ingestion endpoint authenticate with a bearer token on their own chain.
    return Set.of("/api/public/**", "/api/ingestion/variable");
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    // Public paths that live under the /api namespace but are not part of the bearer API surface.
    return Set.of(
        "/api/readyz",
        "/api/ui-configuration",
        "/api/localization",
        "/api/authentication/callback",
        "/api/authentication/logout",
        "/api/external/**");
  }

  @Override
  public Set<String> unprotectedPaths() {
    // Public, non-API paths: embedded share UI, static resources, actuator.
    return Set.of(
        "/external/**",
        "/static/**",
        "/*.ico",
        "/*.html",
        "/actuator/**");
  }

  @Override
  public Set<String> webappPaths() {
    // Catch-all: the webapp chain handles every path not claimed by a higher-priority chain
    // (unprotected chain at order 0, bearer API chain at order 1). See
    // OptimizeWebappSecurityConfiguration for why this must sort BELOW the API chain.
    return Set.of("/**");
  }

  @Override
  public Set<String> webComponentNames() {
    return Set.of("optimize");
  }
}

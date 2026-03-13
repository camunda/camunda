/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spi;

import java.util.Set;

/**
 * SPI for defining the HTTP path patterns used by gatekeeper's security filter chains. Each
 * consuming application must implement this interface to declare which paths are API endpoints,
 * which are unprotected, which serve web application UI, and which web components exist.
 *
 * <p>Path patterns use Spring Security's ant-style syntax ({@code **} for multi-level, {@code *}
 * for single-level).
 */
public interface SecurityPathProvider {

  /** Paths that match API endpoints (e.g., {@code "/api/**"}, {@code "/v2/**"}). */
  Set<String> apiPaths();

  /**
   * API paths accessible without authentication (e.g., {@code "/v2/license"}, {@code
   * "/v2/status"}). These must be a subset of {@link #apiPaths()}.
   */
  Set<String> unprotectedApiPaths();

  /**
   * Non-API paths accessible without authentication (e.g., {@code "/actuator/**"}, {@code
   * "/error"}).
   */
  Set<String> unprotectedPaths();

  /** Paths serving web application UI (e.g., {@code "/login/**"}, {@code "/operate/**"}). */
  Set<String> webappPaths();

  /**
   * Web component names for authorization checks — bare path segment identifiers, not ant-style
   * patterns (e.g., {@code "operate"}, {@code "hub"}, not {@code "/operate/**"}).
   */
  Set<String> webComponentNames();
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import java.util.regex.Pattern;

/**
 * Lightweight security config holder for the engine and broker processing chain. Replaces the
 * former {@code SecurityConfiguration} in non-Spring contexts so that the engine and broker modules
 * depend only on {@code camunda-security-library-api} — not on Spring Boot starter beans.
 *
 * <p>Constructed by the Spring entry-point ({@code BrokerModuleConfiguration}) from OC's {@code
 * SecurityConfiguration} and passed down the non-Spring partition startup chain. A follow-up PR
 * will switch the source to CSL's {@code CamundaSecurityLibraryProperties} and delete {@code
 * SecurityConfiguration}.
 *
 * <p>Note: {@link AuthenticationConfiguration} and {@link InitializationConfiguration} are kept
 * here as sub-objects as a temporary measure. They will be removed once the gRPC Gateway gets its
 * own config path (removing the need for auth config here) and identity setup is extracted out of
 * the engine. See: https://github.com/camunda/camunda-security-library/issues/274
 */
public final class EngineSecurityConfig {

  private final AuthenticationConfiguration authentication;
  private final boolean authorizationsEnabled;
  private final boolean multiTenancyChecksEnabled;
  private final InitializationConfiguration initialization;
  private final Pattern idValidationPattern;
  private final Pattern groupIdValidationPattern;

  public EngineSecurityConfig(
      final AuthenticationConfiguration authentication,
      final boolean authorizationsEnabled,
      final boolean multiTenancyChecksEnabled,
      final InitializationConfiguration initialization,
      final Pattern idValidationPattern,
      final Pattern groupIdValidationPattern) {
    this.authentication = authentication;
    this.authorizationsEnabled = authorizationsEnabled;
    this.multiTenancyChecksEnabled = multiTenancyChecksEnabled;
    this.initialization = initialization;
    this.idValidationPattern = idValidationPattern;
    this.groupIdValidationPattern = groupIdValidationPattern;
  }

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public boolean isAuthorizationsEnabled() {
    return authorizationsEnabled;
  }

  /** Returns a copy of this config with {@code authorizationsEnabled} set to the given value. */
  public EngineSecurityConfig withAuthorizationsEnabled(final boolean authorizationsEnabled) {
    return new EngineSecurityConfig(
        authentication,
        authorizationsEnabled,
        multiTenancyChecksEnabled,
        initialization,
        idValidationPattern,
        groupIdValidationPattern);
  }

  public boolean isMultiTenancyChecksEnabled() {
    return multiTenancyChecksEnabled;
  }

  /**
   * Returns a copy of this config with {@code multiTenancyChecksEnabled} set to the given value.
   */
  public EngineSecurityConfig withMultiTenancyChecksEnabled(
      final boolean multiTenancyChecksEnabled) {
    return new EngineSecurityConfig(
        authentication,
        authorizationsEnabled,
        multiTenancyChecksEnabled,
        initialization,
        idValidationPattern,
        groupIdValidationPattern);
  }

  public InitializationConfiguration getInitialization() {
    return initialization;
  }

  /**
   * Returns the compiled regex {@link Pattern} used to validate user/resource identifier strings.
   * Returns {@code null} if no pattern is configured (validation disabled).
   */
  public Pattern getIdValidationPattern() {
    return idValidationPattern;
  }

  /**
   * Returns the compiled regex {@link Pattern} used to validate group identifier strings. Returns
   * {@code null} if no pattern is configured (validation disabled).
   */
  public Pattern getGroupIdValidationPattern() {
    return groupIdValidationPattern;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.validation.IdentifierValidator;

/**
 * Lightweight security config holder for the engine and broker processing chain. Replaces the
 * former {@code SecurityConfiguration} in non-Spring contexts so that the engine and broker modules
 * depend only on {@code camunda-security-library-api} and {@code camunda-security-validation} — not
 * on Spring Boot starter beans.
 *
 * <p>Constructed by the Spring entry-point ({@code BrokerModuleConfiguration}) from the canonical
 * {@code CamundaSecurityLibraryProperties} and passed down the non-Spring partition startup chain.
 */
public final class EngineSecurityConfig {

  private final AuthenticationConfiguration authentication;
  private final AuthorizationsConfiguration authorizations;
  private final MultiTenancyConfiguration multiTenancy;
  private final InitializationConfiguration initialization;
  private final IdentifierValidator identifierValidator;

  public EngineSecurityConfig(
      final AuthenticationConfiguration authentication,
      final AuthorizationsConfiguration authorizations,
      final MultiTenancyConfiguration multiTenancy,
      final InitializationConfiguration initialization,
      final IdentifierValidator identifierValidator) {
    this.authentication = authentication;
    this.authorizations = authorizations;
    this.multiTenancy = multiTenancy;
    this.initialization = initialization;
    this.identifierValidator = identifierValidator;
  }

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public AuthorizationsConfiguration getAuthorizations() {
    return authorizations;
  }

  public MultiTenancyConfiguration getMultiTenancy() {
    return multiTenancy;
  }

  public InitializationConfiguration getInitialization() {
    return initialization;
  }

  public IdentifierValidator getIdentifierValidator() {
    return identifierValidator;
  }
}

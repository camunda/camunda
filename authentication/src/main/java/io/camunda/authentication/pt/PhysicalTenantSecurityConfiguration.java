/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Enables Spring Security's web infrastructure under the {@code pt-security} profile and installs
 * the {@link PhysicalTenantSecurityChainRegistrar} which programmatically registers one {@link
 * org.springframework.security.web.SecurityFilterChain} bean per (tenant × variant) combination.
 *
 * <p>The explicit per-tenant {@code @Bean} methods this configuration used to carry were replaced
 * (Task 13) with a single {@link
 * org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor} that enumerates
 * tenant ids from {@code camunda.physical-tenants.*} (via {@link
 * org.springframework.boot.context.properties.bind.Binder} — same pattern as the OIDC and session
 * repos elsewhere in the module). Adding a new tenant to {@code application-pt-poc.yaml} grows the
 * chain set without touching Java.
 *
 * <p>The registrar is declared as a {@code static @Bean} so it is instantiated before
 * configuration-class post-processing, which is required for any {@code
 * BeanDefinitionRegistryPostProcessor}.
 */
@Configuration
@EnableWebSecurity
@Profile("pt-security")
public class PhysicalTenantSecurityConfiguration {

  @Bean
  public static PhysicalTenantSecurityChainRegistrar physicalTenantSecurityChainRegistrar() {
    return new PhysicalTenantSecurityChainRegistrar();
  }
}

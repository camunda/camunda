/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SPIKE (ADR-0036): opt-in configuration that adopts CSL for Optimize.
 *
 * <p>Activated by {@code optimize.security.csl.enabled=true}. The legacy adapters
 * ({@code CCSMSecurityConfigurerAdapter} / {@code CCSaaSSecurityConfigurerAdapter}) carry the
 * inverse condition, so exactly one security setup is active. This makes the spike manually
 * testable by flipping a single flag.
 *
 * <p>Because CSL now gives the API and webapp chains distinct orders (API before webapp,
 * ADR-0036), Optimize can use the umbrella {@code CamundaSecurityAutoConfiguration} directly and
 * return {@code /**} from {@link SecurityPathPort#webappPaths()}: the stock webapp chain becomes
 * the catch-all that sorts below the bearer API chain. No custom webapp chain bean is needed.
 *
 * <p>Required application config (see {@code application-csl-spike.yaml}):
 *
 * <ul>
 *   <li>{@code optimize.security.csl.enabled=true}
 *   <li>{@code camunda.security.authentication.method=oidc}
 *   <li>{@code camunda.security.unhandled-paths-chain.enabled=false} (so the CSL deny chain does
 *       not collide with the {@code /**} webapp chain)
 *   <li>{@code camunda.security.authentication.oidc.*} for the Identity (CCSM) / Auth0 (CCSaaS)
 *       client registration
 * </ul>
 *
 * <p>Session storage: for single-node manual testing this leaves persistent sessions off, so CSL
 * uses the in-memory servlet {@code HttpSession} and no {@code SessionStorePort} bean is required.
 * Multi-instance deployments need an Elasticsearch-backed {@code SessionStorePort} (see
 * {@link OptimizeSessionStoreAdapter} and SPIKE-NOTES.md).
 */
@Configuration
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)
public class OptimizeCamundaSecurityConfig {

  @Bean
  @ConditionalOnMissingBean
  public SecurityPathPort securityPathPort() {
    return new OptimizeSecurityPathAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public MembershipPort membershipPort() {
    return new OptimizeMembershipAdapter();
  }
}

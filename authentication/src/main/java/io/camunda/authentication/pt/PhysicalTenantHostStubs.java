/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Stand-in beans for the walking-skeleton PT security setup.
 *
 * <p>A handful of OC beans defined in {@link
 * io.camunda.authentication.config.OidcOverrideBeansConfiguration} (which is active whenever {@code
 * authentication.method=oidc}) consume Spring-Security collaborators that are normally produced as
 * a side-effect of CSL's {@code oauth2Login()} application on a webapp chain — in setups where no
 * CSL chain installs {@code oauth2Login} (e.g. some PT shapes), they need to be provided
 * explicitly.
 *
 * <p>Beans currently stubbed:
 *
 * <ul>
 *   <li>{@link OAuth2AuthorizedClientRepository} — consumed by {@code
 *       OidcOverrideBeansConfiguration#oidcUserAuthenticationConverter} and {@code
 *       authorizedClientManager}. Spring Security's default, {@link
 *       HttpSessionOAuth2AuthorizedClientRepository}, persists authorized clients in the servlet
 *       session and is exactly what {@code oauth2Login()} would install when no explicit repository
 *       is configured.
 * </ul>
 *
 * <p>This whole file is walking-skeleton scaffolding — the chain-factory work in subsequent tasks
 * folds these stubs into the per-tenant chain configuration so the host context doesn't need any
 * ambient OAuth2 beans at all.
 */
@Configuration
public class PhysicalTenantHostStubs {

  @Bean
  @ConditionalOnMissingBean
  public OAuth2AuthorizedClientRepository oauth2AuthorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
  }
}

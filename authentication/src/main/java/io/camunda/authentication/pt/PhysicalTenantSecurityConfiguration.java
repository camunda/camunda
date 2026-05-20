/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.configuration.SecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@Profile("pt-security")
public class PhysicalTenantSecurityConfiguration {

  // Hard-coded for the walking skeleton. Reads a NAMED OIDC provider "tenanta"
  // from camunda.security.authentication.providers.oidc.tenanta.*. The cluster-
  // default authentication.oidc.* slot is reserved for the default tenant (added
  // in Task 5). Each tenant binds its own Keycloak realm to its own provider
  // config — the registration id matches the tenant id for tenant A, and is
  // "oidc" for the default tenant.
  private static final String TENANTA_REGISTRATION_ID = "tenanta";

  @Bean
  public SecurityFilterChain ptTenantaWebappChain(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {

    final var providerConfig =
        security.getAuthentication().getProviders().getOidc().get(TENANTA_REGISTRATION_ID);

    final ClientRegistration registration =
        ClientRegistrations.fromIssuerLocation(providerConfig.getIssuerUri())
            .registrationId(TENANTA_REGISTRATION_ID)
            .clientId(providerConfig.getClientId())
            .clientSecret(providerConfig.getClientSecret())
            .redirectUri("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}")
            .build();

    final ClientRegistrationRepository repo =
        new InMemoryClientRegistrationRepository(registration);

    return http.securityMatcher("/physical-tenant/tenanta/**")
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(repo)
                    .authorizationEndpoint(
                        ae -> ae.baseUri("/physical-tenant/tenanta/oauth2/authorization"))
                    .redirectionEndpoint(
                        re -> re.baseUri("/physical-tenant/tenanta/login/oauth2/code/*")))
        .build();
  }
}

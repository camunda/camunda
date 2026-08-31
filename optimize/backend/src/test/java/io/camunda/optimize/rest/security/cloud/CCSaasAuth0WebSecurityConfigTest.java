/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@ExtendWith(MockitoExtension.class)
class CCSaasAuth0WebSecurityConfigTest {

  // Distinct on purpose: distinguishes which of the two the issuer/JWKS URI is built from, rather
  // than passing accidentally because both configured values happen to match.
  private static final String BACKEND_DOMAIN = "tenant.eu.auth0.com";
  private static final String CUSTOM_DOMAIN = "weblogin.example.com";

  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private CloudAuthConfiguration cloudAuthConfiguration;

  @Test
  void shouldBuildIssuerUriFromCustomDomainNotBackendDomain() {
    stubCloudAuthConfiguration();

    final ClientRegistration registration = registration();

    assertThat(registration.getProviderDetails().getIssuerUri())
        .isEqualTo("https://" + CUSTOM_DOMAIN + "/");
  }

  @Test
  void shouldBuildJwksUriFromBackendDomainNotCustomDomain() {
    stubCloudAuthConfiguration();

    final ClientRegistration registration = registration();

    assertThat(registration.getProviderDetails().getJwkSetUri())
        .isEqualTo("https://" + BACKEND_DOMAIN + "/.well-known/jwks.json");
  }

  private ClientRegistration registration() {
    final CCSaasAuth0WebSecurityConfig config =
        new CCSaasAuth0WebSecurityConfig(configurationService);
    final ClientRegistrationRepository repository = config.clientRegistrationRepository();
    return repository.findByRegistrationId(
        CCSaasAuth0WebSecurityConfig.AUTH_0_CLIENT_REGISTRATION_ID);
  }

  private void stubCloudAuthConfiguration() {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getDomain()).thenReturn(BACKEND_DOMAIN);
    when(cloudAuthConfiguration.getCustomDomain()).thenReturn(CUSTOM_DOMAIN);
    when(cloudAuthConfiguration.getClientId()).thenReturn("optimize-client-id");
    when(cloudAuthConfiguration.getClientSecret()).thenReturn("client-secret");
    lenient().when(cloudAuthConfiguration.getClusterId()).thenReturn("cluster-1");
    lenient()
        .when(cloudAuthConfiguration.getUserAccessTokenAudience())
        .thenReturn(Optional.empty());
  }
}

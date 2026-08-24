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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@ExtendWith(MockitoExtension.class)
class CCSaasAuth0WebSecurityConfigTest {

  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private CloudAuthConfiguration cloudAuthConfiguration;

  @ParameterizedTest
  @ValueSource(strings = {"tenant.auth0.com", "tenant.auth0.com/"})
  void shouldBuildIssuerUriWithoutDoubleSlashRegardlessOfTrailingSlashOnDomain(
      final String domain) {
    stubCloudAuthConfiguration(domain);

    final CCSaasAuth0WebSecurityConfig config =
        new CCSaasAuth0WebSecurityConfig(configurationService);
    final ClientRegistrationRepository repository = config.clientRegistrationRepository();
    final ClientRegistration registration =
        repository.findByRegistrationId(CCSaasAuth0WebSecurityConfig.AUTH_0_CLIENT_REGISTRATION_ID);

    assertThat(registration.getProviderDetails().getIssuerUri())
        .isEqualTo("https://tenant.auth0.com/");
  }

  @Test
  void shouldBuildJwksUriUnaffectedByIssuerNormalization() {
    stubCloudAuthConfiguration("tenant.auth0.com");

    final CCSaasAuth0WebSecurityConfig config =
        new CCSaasAuth0WebSecurityConfig(configurationService);
    final ClientRegistrationRepository repository = config.clientRegistrationRepository();
    final ClientRegistration registration =
        repository.findByRegistrationId(CCSaasAuth0WebSecurityConfig.AUTH_0_CLIENT_REGISTRATION_ID);

    assertThat(registration.getProviderDetails().getJwkSetUri())
        .isEqualTo("https://tenant.auth0.com/.well-known/jwks.json");
  }

  private void stubCloudAuthConfiguration(final String domain) {
    when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    when(authConfiguration.getCloudAuthConfiguration()).thenReturn(cloudAuthConfiguration);
    when(cloudAuthConfiguration.getDomain()).thenReturn(domain);
    when(cloudAuthConfiguration.getClientId()).thenReturn("optimize-client-id");
    when(cloudAuthConfiguration.getClientSecret()).thenReturn("client-secret");
    lenient().when(cloudAuthConfiguration.getClusterId()).thenReturn("cluster-1");
    lenient()
        .when(cloudAuthConfiguration.getUserAccessTokenAudience())
        .thenReturn(java.util.Optional.empty());
  }
}

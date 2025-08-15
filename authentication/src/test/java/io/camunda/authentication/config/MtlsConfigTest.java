/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.authentication.converter.PreAuthenticatedAuthenticationTokenConverter;
import io.camunda.authentication.filters.MutualTlsAuthenticationFilter;
import io.camunda.authentication.providers.MutualTlsAuthenticationProvider;
import io.camunda.authentication.service.CertificateUserService;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class MtlsConfigTest {

  @Mock private MutualTlsProperties mtlsProperties;
  @Mock private CertificateUserService certificateUserService;
  @Mock private RoleServices roleServices;
  @Mock private GroupServices groupServices;
  @Mock private TenantServices tenantServices;

  private final MtlsConfig config = new MtlsConfig();

  @Test
  void shouldCreateMutualTlsAuthenticationProvider() {
    // Given
    when(mtlsProperties.getDefaultRoles()).thenReturn(List.of("ROLE_USER"));

    // When
    final MutualTlsAuthenticationProvider provider =
        config.mutualTlsAuthenticationProvider(mtlsProperties);

    // Then
    assertThat(provider).isNotNull();
  }

  @Test
  void shouldCreateMutualTlsAuthenticationFilterWithUserService() {
    // Given
    final MutualTlsAuthenticationProvider provider =
        new MutualTlsAuthenticationProvider(mtlsProperties);

    // When
    final MutualTlsAuthenticationFilter filter =
        config.mutualTlsAuthenticationFilter(provider, Optional.of(certificateUserService));

    // Then
    assertThat(filter).isNotNull();
  }

  @Test
  void shouldCreateMutualTlsAuthenticationFilterWithoutUserService() {
    // Given
    final MutualTlsAuthenticationProvider provider =
        new MutualTlsAuthenticationProvider(mtlsProperties);

    // When
    final MutualTlsAuthenticationFilter filter =
        config.mutualTlsAuthenticationFilter(provider, Optional.empty());

    // Then
    assertThat(filter).isNotNull();
  }

  @Test
  void shouldCreatePreAuthenticatedAuthenticationConverter() {
    // When
    final CamundaAuthenticationConverter<Authentication> converter =
        config.preAuthenticatedAuthenticationConverter(roleServices, groupServices, tenantServices);

    // Then
    assertThat(converter).isNotNull();
    assertThat(converter).isInstanceOf(PreAuthenticatedAuthenticationTokenConverter.class);
  }
}

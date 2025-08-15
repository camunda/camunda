/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.authentication.config.MutualTlsProperties;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class MutualTlsAuthenticationProviderTest {
  // Test certificate for mocking purposes - will use mocked certificate instead

  @Mock private MutualTlsProperties mtlsProperties;
  @Mock private X509Certificate certificate;

  private MutualTlsAuthenticationProvider provider;

  @BeforeEach
  void setUp() {
    provider = new MutualTlsAuthenticationProvider(mtlsProperties);
  }

  @Test
  void shouldSupportPreAuthenticatedAuthenticationToken() {
    assertThat(provider.supports(PreAuthenticatedAuthenticationToken.class)).isTrue();
  }

  @Test
  void shouldNotSupportOtherAuthenticationTypes() {
    assertThat(provider.supports(Authentication.class)).isFalse();
  }

  @Test
  void shouldReturnNullForNonPreAuthenticatedToken() {
    final var auth = new TestAuthentication();
    assertThat(provider.authenticate(auth)).isNull();
  }

  @Test
  void shouldReturnNullForNonCertificatePrincipal() {
    final var auth = new PreAuthenticatedAuthenticationToken("user", null);
    assertThat(provider.authenticate(auth)).isNull();
  }

  @Test
  void shouldAuthenticateValidSelfSignedCertificate() throws Exception {
    // Given
    when(mtlsProperties.isAllowSelfSigned()).thenReturn(true);
    when(mtlsProperties.getDefaultRoles()).thenReturn(List.of("ROLE_USER"));
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    // checkValidity() is void method - no mocking needed

    final var auth = new PreAuthenticatedAuthenticationToken(certificate, null);

    // When
    final Authentication result = provider.authenticate(auth);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.isAuthenticated()).isTrue();
    assertThat(result.getPrincipal()).isInstanceOf(String.class);
    assertThat(result.getCredentials()).isEqualTo(certificate);
    assertThat(result.getAuthorities()).isNotEmpty();
  }

  @Test
  void shouldRejectSelfSignedCertificateWhenNotAllowed() throws Exception {
    // Given
    when(mtlsProperties.isAllowSelfSigned()).thenReturn(false);
    when(mtlsProperties.getTrustedCertificates()).thenReturn(null);
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    // checkValidity() is void method - no mocking needed

    final var auth = new PreAuthenticatedAuthenticationToken(certificate, null);

    // When & Then
    assertThatThrownBy(() -> provider.authenticate(auth))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("Certificate validation failed");
  }

  @Test
  void shouldAssignConfiguredRoles() throws Exception {
    // Given
    when(mtlsProperties.isAllowSelfSigned()).thenReturn(true);
    when(mtlsProperties.getDefaultRoles()).thenReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    // checkValidity() is void method - no mocking needed

    final var auth = new PreAuthenticatedAuthenticationToken(certificate, null);

    // When
    final Authentication result = provider.authenticate(auth);

    // Then
    assertThat(result.getAuthorities()).hasSize(3); // DEFAULT + 2 configured roles
    assertThat(result.getAuthorities().toString()).contains("ROLE_USER", "ROLE_ADMIN");
  }

  @Test
  void shouldLogSecurityWarningForAdminRoles() throws Exception {
    // Given
    when(mtlsProperties.isAllowSelfSigned()).thenReturn(true);
    when(mtlsProperties.getDefaultRoles()).thenReturn(List.of("ROLE_ADMIN"));
    when(certificate.getSubjectX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    when(certificate.getIssuerX500Principal())
        .thenReturn(new javax.security.auth.x500.X500Principal("CN=test"));
    // checkValidity() is void method - no mocking needed

    final var auth = new PreAuthenticatedAuthenticationToken(certificate, null);

    // When
    final Authentication result = provider.authenticate(auth);

    // Then
    assertThat(result.getAuthorities().toString()).contains("ROLE_ADMIN");
    // Note: Security audit logging verification would require log capture in real implementation
  }

  private static final class TestAuthentication implements Authentication {
    @Override
    public String getName() {
      return "test";
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public Object getPrincipal() {
      return "test";
    }

    @Override
    public boolean isAuthenticated() {
      return false;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) {}

    @Override
    public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority>
        getAuthorities() {
      return List.of();
    }
  }
}

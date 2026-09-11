/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.authentication.exception.TokenVerificationException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OptimizeCcsmSecurityConfigurationTest {

  @Mock private OidcProviderConfigurationPort oidcProviderConfigurationPort;
  @Mock private CCSMTokenService ccsmTokenService;

  private final OptimizeCcsmSecurityConfiguration config = new OptimizeCcsmSecurityConfiguration();
  private final CamundaSecurityLibraryProperties cslProperties =
      new CamundaSecurityLibraryProperties();

  private OAuth2TokenValidator<Jwt> sharedValidator() {
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    final TokenValidatorFactory factory =
        config.tokenValidatorFactory(
            oidcProviderConfigurationPort, cslProperties, ccsmTokenService);
    return factory.createTokenValidator(clientRegistration());
  }

  @Test
  void shouldAcceptTokenGrantedTheOptimizePermission() {
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    assertThat(validator.validate(jwt()).hasErrors()).isFalse();
  }

  @Test
  void shouldRejectTokenIdentityCannotVerify() {
    // Restores the CCSM Identity write:* gate CSL otherwise skips entirely: without this
    // TokenValidatorFactory override, CSL's default only checks issuer/signature/expiry.
    doThrow(new TokenVerificationException("token invalid"))
        .when(ccsmTokenService)
        .verifyAccessToken("token");
    final OAuth2TokenValidator<Jwt> validator = sharedValidator();

    assertThat(validator.validate(jwt()).hasErrors()).isTrue();
  }

  @Test
  void shouldBuildOidcIdTokenDecoderFactoryForLogin() {
    when(oidcProviderConfigurationPort.getOidcAuthenticationConfigurations()).thenReturn(Map.of());

    final TokenValidatorFactory factory =
        config.tokenValidatorFactory(
            oidcProviderConfigurationPort, cslProperties, ccsmTokenService);

    assertThat(config.idTokenDecoderFactory(factory)).isInstanceOf(OidcIdTokenDecoderFactory.class);
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId("oidc")
        .clientId("optimize")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/api/authentication/callback")
        .authorizationUri("http://idp/authorize")
        .tokenUri("http://idp/token")
        .build();
  }

  private static Jwt jwt() {
    final Instant now = Instant.now();
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(300))
        .subject("user")
        .build();
  }
}

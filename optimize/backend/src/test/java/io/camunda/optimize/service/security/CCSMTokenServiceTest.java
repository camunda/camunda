/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.collect.ImmutableList;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.authentication.UserDetails;
import io.camunda.identity.sdk.authentication.exception.TokenDecodeException;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CCSMAuthConfiguration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@ExtendWith(MockitoExtension.class)
public class CCSMTokenServiceTest {

  private static final String ACCESS_TOKEN_VALUE = "accessToken";
  private static final String OPTIMIZE_PERMISSION = "write:*";

  private static final String EMAIL = "user@example.com";
  private static final String ID = "user123";
  private static final String NAME = "name";
  private static final String USERNAME = "username";

  @Mock private AuthCookieService authCookieService;
  @Mock private ConfigurationService configurationService;
  @Mock private AuthConfiguration authConfiguration;
  @Mock private CCSMAuthConfiguration ccsmAuthConfiguration;
  @Mock private Identity identity;
  @Mock private Authentication authentication;
  @Mock private AccessToken accessToken;
  @Mock private UserDetails userDetails;
  @Mock private DecodedJWT decodedJWT;
  @Mock private Claim verClaim;
  @Mock private ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider;

  private CCSMTokenService ccsmTokenService;

  @BeforeEach
  void setUp() {
    // Baseline stubs used by most tests; lenient because static-method tests bypass the service
    lenient().when(identity.authentication()).thenReturn(authentication);
    lenient().when(authentication.verifyToken(ACCESS_TOKEN_VALUE)).thenReturn(accessToken);
    lenient().when(accessToken.getPermissions()).thenReturn(ImmutableList.of(OPTIMIZE_PERMISSION));
    lenient().when(authentication.decodeJWT(ACCESS_TOKEN_VALUE)).thenReturn(decodedJWT);
    lenient().when(decodedJWT.getIssuer()).thenReturn("https://idp.example.com");
    // Default: Entra version check is enabled (the production default)
    lenient().when(configurationService.getAuthConfiguration()).thenReturn(authConfiguration);
    lenient().when(authConfiguration.getCcsmAuthConfiguration()).thenReturn(ccsmAuthConfiguration);
    lenient().when(ccsmAuthConfiguration.isEntraTokenVersionCheckEnabled()).thenReturn(true);

    ccsmTokenService =
        new CCSMTokenService(
            authCookieService, configurationService, identity, authorizedClientRepositoryProvider);
  }

  @Test
  void getUserInfoFromTokenValidTokenReturnsUserDto() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.of(NAME));
    when(userDetails.getEmail()).thenReturn(Optional.of(EMAIL));

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertThat(result.getId()).isEqualTo(ID);
    assertThat(result.getFirstName()).isEqualTo(NAME);
    assertThat(result.getEmail()).isEqualTo(EMAIL);
    assertThat(result.getLastName()).isNull();
    assertThat(result.getRoles().isEmpty()).isTrue();
  }

  @Test
  void getUserInfoFromTokenMissingNameReturnsUsername() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.empty());
    when(userDetails.getUsername()).thenReturn(Optional.of(USERNAME));
    when(userDetails.getEmail()).thenReturn(Optional.of(EMAIL));

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertThat(result.getId()).isEqualTo(ID);
    assertThat(result.getFirstName()).isEqualTo(USERNAME);
    assertThat(result.getEmail()).isEqualTo(EMAIL);
    assertThat(result.getLastName()).isNull();
    assertThat(result.getRoles().isEmpty()).isTrue();
  }

  @Test
  void getUserInfoFromTokenMissingNameAndUsernameReturnsUserIdAsUsername() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.empty());
    when(userDetails.getUsername()).thenReturn(Optional.empty());
    when(userDetails.getEmail()).thenReturn(Optional.of(EMAIL));

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertThat(result.getId()).isEqualTo(ID);
    assertThat(result.getFirstName()).isEqualTo(ID);
    assertThat(result.getEmail()).isEqualTo(EMAIL);
    assertThat(result.getLastName()).isNull();
    assertThat(result.getRoles().isEmpty()).isTrue();
  }

  @Test
  void getUserInfoFromTokenMissingEmailReturnsUserIdAsEmail() {
    when(accessToken.getUserDetails()).thenReturn(userDetails);
    when(userDetails.getName()).thenReturn(Optional.of(NAME));
    when(userDetails.getEmail()).thenReturn(Optional.empty());

    final UserDto result = ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE);

    assertThat(result.getId()).isEqualTo(ID);
    assertThat(result.getEmail()).isEqualTo(ID);
    assertThat(result.getFirstName()).isEqualTo(NAME);
    assertThat(result.getLastName()).isNull();
    assertThat(result.getRoles().isEmpty()).isTrue();
  }

  @Test
  void getUserInfoFromTokenInvalidTokenThrowsNotAuthorizedException() {
    when(accessToken.getPermissions()).thenReturn(ImmutableList.of());

    assertThatExceptionOfType(NotAuthorizedException.class)
        .isThrownBy(() -> ccsmTokenService.getUserInfoFromToken(ID, ACCESS_TOKEN_VALUE));
  }

  // --- isMicrosoftEntraIssuer ---

  @Test
  void shouldRecognizeStsWindowsNetIssuer() {
    assertThat(CCSMTokenService.isMicrosoftEntraIssuer("https://sts.windows.net/tenant-id/"))
        .isTrue();
  }

  @Test
  void shouldRecognizeLoginMicrosoftonlineComIssuer() {
    assertThat(
            CCSMTokenService.isMicrosoftEntraIssuer(
                "https://login.microsoftonline.com/tenant-id/v2.0"))
        .isTrue();
  }

  @Test
  void shouldRejectNonMicrosoftIssuer() {
    assertThat(CCSMTokenService.isMicrosoftEntraIssuer("https://idp.example.com")).isFalse();
  }

  @Test
  void shouldRecognizeUsGovSovereignCloudIssuer() {
    assertThat(
            CCSMTokenService.isMicrosoftEntraIssuer(
                "https://login.microsoftonline.us/tenant-id/v2.0"))
        .isTrue();
  }

  @Test
  void shouldRecognizeGermanySovereignCloudIssuer() {
    assertThat(
            CCSMTokenService.isMicrosoftEntraIssuer(
                "https://login.microsoftonline.de/tenant-id/v2.0"))
        .isTrue();
  }

  @Test
  void shouldRecognizeChinaSovereignCloudLoginIssuer() {
    assertThat(
            CCSMTokenService.isMicrosoftEntraIssuer(
                "https://login.partner.microsoftonline.cn/tenant-id/v2.0"))
        .isTrue();
  }

  @Test
  void shouldRecognizeChinaSovereignCloudStsIssuer() {
    assertThat(CCSMTokenService.isMicrosoftEntraIssuer("https://sts.chinacloudapi.cn/tenant-id/"))
        .isTrue();
  }

  // --- verifyToken Entra version guard ---

  @Test
  void shouldRejectMicrosoftV1TokenOnVerifyToken() {
    // given — token from sts.windows.net with ver=1.0
    when(decodedJWT.getIssuer()).thenReturn("https://sts.windows.net/tenant-id/");
    when(decodedJWT.getClaim("ver")).thenReturn(verClaim);
    when(verClaim.asString()).thenReturn("1.0");

    // when / then
    assertThatThrownBy(() -> ccsmTokenService.verifyToken(ACCESS_TOKEN_VALUE))
        .isInstanceOf(NotAuthorizedException.class)
        .hasMessageContaining("ver")
        .hasMessageContaining("2.0")
        .hasMessageContaining("api.requestedAccessTokenVersion");
  }

  @Test
  void shouldAcceptMicrosoftV2TokenOnVerifyToken() {
    // given — valid v2.0 Entra token
    when(decodedJWT.getIssuer()).thenReturn("https://login.microsoftonline.com/tenant/v2.0");
    when(decodedJWT.getClaim("ver")).thenReturn(verClaim);
    when(verClaim.asString()).thenReturn("2.0");

    // when
    final AccessToken result = ccsmTokenService.verifyToken(ACCESS_TOKEN_VALUE);

    // then — no exception; returns the verified token
    assertThat(result).isSameAs(accessToken);
  }

  @Test
  void shouldPassNonMicrosoftTokenWithoutVerCheck() {
    // given — Keycloak issuer, no ver claim expected
    when(decodedJWT.getIssuer()).thenReturn("https://keycloak.example.com/realms/camunda");

    // when / then — no exception
    final AccessToken result = ccsmTokenService.verifyToken(ACCESS_TOKEN_VALUE);
    assertThat(result).isSameAs(accessToken);
  }

  @Test
  void shouldHandleTokenDecodeExceptionGracefully() {
    // given — decodeJWT throws (e.g. opaque token, not a JWT)
    when(authentication.decodeJWT(ACCESS_TOKEN_VALUE))
        .thenThrow(new TokenDecodeException(new RuntimeException("not a jwt")));

    // when / then — no exception from the Entra check; normal verification succeeds
    final AccessToken result = ccsmTokenService.verifyToken(ACCESS_TOKEN_VALUE);
    assertThat(result).isSameAs(accessToken);
  }

  // --- verifyAccessToken Entra version guard ---

  @Test
  void shouldRejectMicrosoftV1TokenOnVerifyAccessToken() {
    // given
    when(decodedJWT.getIssuer()).thenReturn("https://sts.windows.net/tenant-id/");
    when(decodedJWT.getClaim("ver")).thenReturn(verClaim);
    when(verClaim.asString()).thenReturn("1.0");

    // when / then
    assertThatThrownBy(() -> ccsmTokenService.verifyAccessToken(ACCESS_TOKEN_VALUE))
        .isInstanceOf(NotAuthorizedException.class)
        .hasMessageContaining("api.requestedAccessTokenVersion");
  }

  @Test
  void shouldSkipEntraCheckWhenCheckDisabledViaConfig() {
    // given — escape-hatch flag is off; issuer/ver stubs deliberately absent because the
    // check returns before decoding the JWT
    when(ccsmAuthConfiguration.isEntraTokenVersionCheckEnabled()).thenReturn(false);

    // when — no exception; check is bypassed regardless of token content
    final AccessToken result = ccsmTokenService.verifyToken(ACCESS_TOKEN_VALUE);

    // then — token accepted despite being v1.0
    assertThat(result).isSameAs(accessToken);
  }
}

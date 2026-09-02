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
import static org.mockito.ArgumentMatchers.eq;
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
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;
  @Mock private OAuth2AuthorizedClient authorizedClient;
  @Mock private OAuth2AccessToken oauth2AccessToken;
  @Mock private HttpServletRequest request;

  @Mock private ObjectProvider<CamundaAuthenticationProvider> camundaAuthenticationProviderProvider;

  @Mock private CamundaAuthenticationProvider camundaAuthenticationProvider;

  private CCSMTokenService ccsmTokenService;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

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
            authCookieService,
            configurationService,
            identity,
            authorizedClientRepositoryProvider,
            camundaAuthenticationProviderProvider);
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

  // --- getCurrentUserAuthToken: token source (CSL OIDC session vs legacy cookie) ---

  @Test
  void shouldResolveAuthTokenFromAuthorizedClientRepositoryUnderCsl() {
    // given — CSL OIDC webapp session: authorized-client repository present, OAuth2 authentication
    setCurrentRequest();
    final OAuth2AuthenticationToken oauthToken = oauthToken();
    SecurityContextHolder.getContext().setAuthentication(oauthToken);
    when(authorizedClientRepositoryProvider.getIfAvailable())
        .thenReturn(authorizedClientRepository);
    when(authorizedClientRepository.loadAuthorizedClient(eq("auth0"), eq(oauthToken), eq(request)))
        .thenReturn(authorizedClient);
    when(authorizedClient.getAccessToken()).thenReturn(oauth2AccessToken);
    when(oauth2AccessToken.getTokenValue()).thenReturn("csl-access-token");

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserAuthToken();

    // then
    assertThat(result).contains("csl-access-token");
  }

  @Test
  void shouldFallBackToAuthCookieWhenNoAuthorizedClientRepository() {
    // given — legacy CCSM: no authorized-client repository, token lives in the auth cookie
    setCurrentRequestWithAuthCookie("cookie-token");
    when(authorizedClientRepositoryProvider.getIfAvailable()).thenReturn(null);

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserAuthToken();

    // then
    assertThat(result).contains("cookie-token");
  }

  @Test
  void shouldFallBackToValidatedBearerTokenWhenNoSessionOrCookie() {
    // given — stateless CSL API caller (e.g. the Web Modeler cluster proxy): no OIDC session and no
    // auth cookie, only a validated bearer token in the security context
    setCurrentRequestWithoutAuthCookie();
    SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("bearer-token"));
    when(camundaAuthenticationProviderProvider.getIfAvailable())
        .thenReturn(camundaAuthenticationProvider);

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserAuthToken();

    // then
    assertThat(result).contains("bearer-token");
  }

  @Test
  void shouldNotFallBackToBearerTokenWhenCslInactive() {
    // given — the legacy public API path also authenticates as a JwtAuthenticationToken, but CSL is
    // not active; the bearer token must not flow into tenant resolution as it did not before
    setCurrentRequestWithoutAuthCookie();
    SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("api-token"));

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserAuthToken();

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldPreferAuthCookieOverBearerTokenWhenBothPresent() {
    // given — a request carrying both a stale auth cookie and a security-context bearer token; the
    // cookie must win so a lingering context token can never override the request's own credential
    setCurrentRequestWithAuthCookie("cookie-token");
    SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("bearer-token"));
    // CSL active, so the bearer token is a live alternative: only the cookie-before-bearer ordering
    // keeps it from winning. Lenient because that ordering short-circuits before the bearer branch.
    lenient()
        .when(camundaAuthenticationProviderProvider.getIfAvailable())
        .thenReturn(camundaAuthenticationProvider);

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserAuthToken();

    // then
    assertThat(result).contains("cookie-token");
  }

  @Test
  void shouldReturnEmptyWhenNoSessionCookieOrBearerToken() {
    // given — an authenticated request that carries none of the supported token sources
    setCurrentRequestWithoutAuthCookie();

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserAuthToken();

    // then
    assertThat(result).isEmpty();
  }

  // --- getCurrentUserIdFromAuthToken: principal source (CSL claim vs legacy sub) ---

  @Test
  void shouldResolveCurrentUserIdFromCslPrincipalRespectingUsernameClaim() {
    // given — CSL resolves the principal from the configured username-claim, not sub
    SecurityContextHolder.getContext().setAuthentication(oauthToken());
    when(camundaAuthenticationProviderProvider.getIfAvailable())
        .thenReturn(camundaAuthenticationProvider);
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("preferred-username")));

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserIdFromAuthToken();

    // then — id comes from the CSL principal, without decoding the token
    assertThat(result).contains("preferred-username");
  }

  @Test
  void shouldResolveCurrentUserIdFromCslPrincipalForBearerRequests() {
    // given — CSL authenticates a bearer request as a JWT; the id must still come from the
    // configured username-claim, not the raw sub claim
    SecurityContextHolder.getContext().setAuthentication(jwtAuthentication("bearer-token"));
    when(camundaAuthenticationProviderProvider.getIfAvailable())
        .thenReturn(camundaAuthenticationProvider);
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(CamundaAuthentication.of(b -> b.user("preferred-username")));

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserIdFromAuthToken();

    // then — id comes from the CSL principal, without decoding the bearer token
    assertThat(result).contains("preferred-username");
  }

  @Test
  void shouldFallBackToSubClaimForCurrentUserIdWhenNotUnderCsl() {
    // given — legacy CCSM: no CSL principal provider, id derived from the token's sub claim
    setCurrentRequestWithAuthCookie(ACCESS_TOKEN_VALUE);
    when(camundaAuthenticationProviderProvider.getIfAvailable()).thenReturn(null);
    when(authorizedClientRepositoryProvider.getIfAvailable()).thenReturn(null);
    when(decodedJWT.getSubject()).thenReturn("sub-user-id");

    // when
    final Optional<String> result = ccsmTokenService.getCurrentUserIdFromAuthToken();

    // then
    assertThat(result).contains("sub-user-id");
  }

  private void setCurrentRequest() {
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private void setCurrentRequestWithAuthCookie(final String token) {
    final Cookie cookie =
        new Cookie(
            AuthCookieService.getAuthorizationCookieNameWithSuffix(0),
            AuthCookieService.createOptimizeAuthCookieValue(token));
    when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    when(request.getCookies()).thenReturn(new Cookie[] {cookie});
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private void setCurrentRequestWithoutAuthCookie() {
    when(request.getAttributeNames()).thenReturn(Collections.emptyEnumeration());
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  private JwtAuthenticationToken jwtAuthentication(final String tokenValue) {
    final Jwt jwt =
        Jwt.withTokenValue(tokenValue).header("alg", "none").claim("sub", "user").build();
    return new JwtAuthenticationToken(jwt);
  }

  private OAuth2AuthenticationToken oauthToken() {
    final OAuth2User user =
        new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            java.util.Map.of("sub", "user"),
            "sub");
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "auth0");
  }
}

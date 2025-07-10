/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import io.camunda.authentication.config.OidcClientRegistration;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class OidcSecurityContextLogoutHandler implements LogoutHandler {

  private static final Logger LOG = LoggerFactory.getLogger(OidcSecurityContextLogoutHandler.class);

  private final ClientRegistrationRepository clientRegistrationRepository;
  private final OAuth2AuthorizedClientService authorizedClientService;
  private final SecurityConfiguration securityConfiguration;
  private final RestTemplate restTemplate;

  public OidcSecurityContextLogoutHandler(
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientService authorizedClientService,
      final SecurityConfiguration securityConfiguration,
      final RestTemplate restTemplate) {
    this.clientRegistrationRepository = clientRegistrationRepository;
    this.authorizedClientService = authorizedClientService;
    this.securityConfiguration = securityConfiguration;
    this.restTemplate = restTemplate;
  }

  @Override
  public void logout(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication) {

    if (authentication == null) {
      return;
    }

    try {
      // execute OIDC logout
      performOidcLogout(authentication);

      // prepare redirect URL if logout was successful
      storeLogoutRedirectUrl(request, authentication);

    } catch (final Exception e) {
      LOG.error(e.getMessage(), e);
    } finally {
      // remove authorized client
      clearAuthorizedClients(authentication);

      // clear session
      final HttpSession session = request.getSession(false);
      if (session != null) {
        session.invalidate();
      }
      // clear context
      SecurityContextHolder.clearContext();
    }
  }

  private void clearAuthorizedClients(final Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken) {
      final OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
      final String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
      final String principalName = authentication.getName();
      authorizedClientService.removeAuthorizedClient(registrationId, principalName);
    }
  }

  private void performOidcLogout(final Authentication authentication) {
    if (!(authentication instanceof OAuth2AuthenticationToken)) {
      LOG.warn(
          "Authentication object is not of type OAuth2AuthenticationToken, skipping OIDC logout.");
      return;
    }

    final OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
    final String registrationId = oauth2Token.getAuthorizedClientRegistrationId();

    final OAuth2AuthorizedClient authorizedClient =
        authorizedClientService.loadAuthorizedClient(registrationId, authentication.getName());

    if (authorizedClient != null) {
      revokeTokensForProvider(authorizedClient);
    }
  }

  private void revokeTokensForProvider(final OAuth2AuthorizedClient authorizedClient) {
    final ClientRegistration clientRegistration = authorizedClient.getClientRegistration();

    try {
      // revoke refresh token first
      final OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
      if (refreshToken != null) {
        revokeToken(
            clientRegistration, refreshToken.getTokenValue(), OAuth2ParameterNames.REFRESH_TOKEN);
      }

      // now access token
      final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
      if (accessToken != null) {
        revokeToken(
            clientRegistration, accessToken.getTokenValue(), OAuth2ParameterNames.ACCESS_TOKEN);
      }

    } catch (final Exception e) {
      // not critical but still a warning
      LOG.warn("Could not revoke token for client: {}", clientRegistration.getClientId(), e);
    }
  }

  private void revokeToken(
      final ClientRegistration clientRegistration, final String token, final String tokenType) {
    final String revokeEndpoint =
        securityConfiguration.getAuthentication().getOidc().getRevokeTokenUri();
    if (revokeEndpoint == null) {
      LOG.warn(
          "Revoke endpoint was not set. Skipping token revocation for token type: {}", tokenType);
      return;
    }

    try {
      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      headers.setBasicAuth(clientRegistration.getClientId(), clientRegistration.getClientSecret());

      final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
      params.add(OAuth2ParameterNames.TOKEN, token);
      params.add(OAuth2ParameterNames.TOKEN_TYPE_HINT, tokenType);

      final HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

      final ResponseEntity<String> response =
          restTemplate.postForEntity(revokeEndpoint, request, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        LOG.warn("Token revocation failed with status: {}", response.getStatusCode());
      }

    } catch (final Exception e) {
      LOG.warn("Error revoking {}", tokenType, e);
    }
  }

  private void storeLogoutRedirectUrl(
      final HttpServletRequest request, final Authentication authentication) {
    final String logoutRedirectUrl = buildOidcLogoutUrl();
    if (logoutRedirectUrl != null) {
      // store in request attribute for LogoutSuccessHandler
      request.setAttribute(
          OidcLogoutSuccessHandler.LOGOUT_SUCCESS_REDIRECT_URL_PROPERTY, logoutRedirectUrl);
    }
  }

  private String buildOidcLogoutUrl() {
    try {
      final ClientRegistration clientRegistration =
          clientRegistrationRepository.findByRegistrationId(OidcClientRegistration.REGISTRATION_ID);

      if (clientRegistration == null) {
        return null;
      }

      final String endSessionEndpoint =
          securityConfiguration.getAuthentication().getOidc().getSessionLogoutUrl();

      if (endSessionEndpoint == null) {
        return null;
      }

      final UriComponentsBuilder logoutUriBuilder =
          UriComponentsBuilder.fromUriString(endSessionEndpoint)
              .queryParam(OAuth2ParameterNames.CLIENT_ID, clientRegistration.getClientId());

      return logoutUriBuilder.build().toString();

    } catch (final Exception e) {
      LOG.warn("Error building OIDC logout url", e);
      return null;
    }
  }
}

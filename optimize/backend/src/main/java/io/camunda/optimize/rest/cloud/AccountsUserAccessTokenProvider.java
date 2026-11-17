/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Conditional(CCSaaSCondition.class)
public class AccountsUserAccessTokenProvider {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AccountsUserAccessTokenProvider.class);

  // Present only under CSL; absent in the legacy CCSaaS setup, which reads the token from the
  // cookie.
  private final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider;

  public AccountsUserAccessTokenProvider(
      final ObjectProvider<OAuth2AuthorizedClientRepository> authorizedClientRepositoryProvider) {
    this.authorizedClientRepositoryProvider = authorizedClientRepositoryProvider;
  }

  private Optional<String> retrieveServiceTokenFromFramework() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof final JwtAuthenticationToken jwt) {
      return Optional.ofNullable(jwt.getToken().getTokenValue());
    }
    // Under CSL the token lives in the OIDC session's authorized client, not a bearer token.
    if (authentication instanceof final OAuth2AuthenticationToken oauthToken) {
      return cslSessionAccessToken(oauthToken);
    }
    if (authentication != null) {
      LOG.info(
          "Could not retrieve access token. Unsupported authentication type {}",
          authentication.getClass().getTypeName());
    }
    return Optional.empty();
  }

  private Optional<String> cslSessionAccessToken(final OAuth2AuthenticationToken oauthToken) {
    final OAuth2AuthorizedClientRepository repository =
        authorizedClientRepositoryProvider == null
            ? null
            : authorizedClientRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return Optional.empty();
    }
    return currentRequest()
        .map(
            request ->
                repository.<OAuth2AuthorizedClient>loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), oauthToken, request))
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(token -> token.getTokenValue());
  }

  private static Optional<HttpServletRequest> currentRequest() {
    return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
        .filter(ServletRequestAttributes.class::isInstance)
        .map(ServletRequestAttributes.class::cast)
        .map(ServletRequestAttributes::getRequest);
  }

  public Optional<String> getCurrentUsersAccessToken() {
    Optional<String> accessToken =
        currentRequest().flatMap(AuthCookieService::getServiceAccessToken);
    // No service-token cookie (e.g. under CSL): fall back to the SecurityContext.
    if (accessToken.isEmpty()) {
      accessToken = retrieveServiceTokenFromFramework();
    }
    return accessToken;
  }
}

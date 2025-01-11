/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.authentication;

import static io.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;

import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.identity.sdk.exception.IdentityException;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.rest.exceptions.NotAuthorizedException;
import io.camunda.optimize.rest.exceptions.NotSupportedException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSMCondition.class)
public class CCSMAuthenticationService extends AbstractAuthenticationService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CCSMAuthenticationService.class);
  private final ConfigurationService configurationService;
  private final CCSMTokenService ccsmTokenService;

  public CCSMAuthenticationService(
      final SessionService sessionService,
      final AuthCookieService authCookieService,
      final CCSMTokenService ccsmTokenService,
      final ConfigurationService configurationService) {
    super(sessionService, authCookieService);
    this.configurationService = configurationService;
    this.ccsmTokenService = ccsmTokenService;
  }

  @Override
  public void authenticateUser(final CredentialsRequestDto credentials) {
    throw new NotSupportedException(
        "Requests to this endpoint are not valid in Camunda Platform Self-Managed mode");
  }

  @Override
  public void loginCallback(
      final AuthCodeDto authCode, final URI uri, final HttpServletResponse response)
      throws IOException {
    final Tokens tokens;
    final AccessToken accessToken;
    try {
      tokens = ccsmTokenService.exchangeAuthCode(authCode, uri);
      accessToken = ccsmTokenService.verifyToken(tokens.getAccessToken());
    } catch (final NotAuthorizedException ex) {
      response.sendError(
          HttpStatus.FORBIDDEN.value(),
          "User has no authorization to access Optimize. Please check your Identity configuration");
      return;
    }
    ccsmTokenService
        .createOptimizeAuthNewCookies(tokens, accessToken, uri.getScheme())
        .forEach(response::addCookie);

    response.sendRedirect(buildRootRedirect(uri));
  }

  @Override
  public void logout(final Cookie[] cookies, final HttpServletResponse response) {
    if (cookies != null) {
      try {
        Arrays.stream(cookies)
            .filter(cookie -> cookie.getName().equals(OPTIMIZE_REFRESH_TOKEN))
            .forEach(refreshCookie -> ccsmTokenService.revokeToken(refreshCookie.getValue()));
      } catch (final IdentityException exception) {
        // We catch the exception even if the token revoke failed, so we can still delete the
        // Optimize cookies
      } finally {
        ccsmTokenService.createOptimizeDeleteAuthNewCookies().forEach(response::addCookie);
      }
    }
  }

  private String buildRootRedirect(final URI uri) {
    final String configuredRedirectRootUrl =
        configurationService.getAuthConfiguration().getCcsmAuthConfiguration().getRedirectRootUrl();
    String redirectUri;
    if (!StringUtils.isEmpty(configuredRedirectRootUrl)) {
      redirectUri = configuredRedirectRootUrl;
    } else {
      redirectUri = uri.getScheme() + "://" + uri.getHost();
      if (
      // value is -1 if no port is set, in that case no need to add it
      uri.getPort() != -1) {
        redirectUri += ":" + uri.getPort();
      }
      redirectUri += configurationService.getContextPath().orElse("/");
    }

    // Instead of redirecting to the home page, we redirect to a redirector that
    // will redirect again to the home page. The reason is that we need to attach
    // auth cookies to the request, and this only happens if the redirection is initiated
    // by a human. Having a redirector that does window.location=<url> simulates the behavior.
    final String targetUri = redirectUri;
    redirectUri += "static/redirect.html?url=" + targetUri;

    LOG.trace("Using root redirect Url: {}", redirectUri);
    return redirectUri;
  }
}

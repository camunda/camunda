/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.identity.sdk.exception.IdentityException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.CCSMTokenService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_REFRESH_TOKEN;

@Component
@Conditional(CCSMCondition.class)
@Slf4j
public class CCSMAuthenticationService extends AbstractAuthenticationService {

  private final ConfigurationService configurationService;
  private final CCSMTokenService ccsmTokenService;

  public CCSMAuthenticationService(final SessionService sessionService,
                                   final AuthCookieService authCookieService,
                                   final CCSMTokenService ccsmTokenService,
                                   final ConfigurationService configurationService) {
    super(sessionService, authCookieService);
    this.configurationService = configurationService;
    this.ccsmTokenService = ccsmTokenService;
  }

  @Override
  public Response authenticateUser(final ContainerRequestContext requestContext,
                                   final CredentialsRequestDto credentials) {
    throw new NotSupportedException("Requests to this endpoint are not valid in Camunda Platform Self-Managed mode");
  }

  @Override
  public Response loginCallback(final ContainerRequestContext requestContext,
                                final AuthCodeDto authCode) {
    final Tokens tokens = ccsmTokenService.exchangeAuthCode(authCode, requestContext);
    final AccessToken accessToken;
    try {
      accessToken = ccsmTokenService.verifyToken(tokens.getAccessToken());
    } catch (NotAuthorizedException ex) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("User has no authorization to access Optimize. Please check your Identity configuration")
        .build();
    }
    final Response.ResponseBuilder responseBuilder = Response.seeOther(URI.create(buildRootRedirect(requestContext)));
    ccsmTokenService.createOptimizeAuthNewCookies(tokens, accessToken, requestContext.getUriInfo().getRequestUri().getScheme())
      .forEach(responseBuilder::cookie);
    return responseBuilder.build();
  }

  @Override
  public Response logout(final ContainerRequestContext requestContext) {
    final Response.ResponseBuilder responseBuilder = Response.status(Response.Status.OK);
    final Map<String, Cookie> cookies = requestContext.getCookies();
    if (cookies != null) {
      try {
        Optional.ofNullable(cookies.get(OPTIMIZE_REFRESH_TOKEN))
          .ifPresent(refreshCookie -> ccsmTokenService.revokeToken(refreshCookie.getValue()));
      } catch (IdentityException exception) {
        // We catch the exception even if the token revoke failed, so we can still delete the Optimize cookies
      } finally {
        ccsmTokenService.createOptimizeDeleteAuthNewCookies().forEach(responseBuilder::cookie);
      }
    }
    return responseBuilder.build();
  }

  private String buildRootRedirect(final ContainerRequestContext requestContext) {
    final String configuredRedirectRootUrl = configurationService.getAuthConfiguration()
      .getCcsmAuthConfiguration()
      .getRedirectRootUrl();
    String redirectUri;
    if (!StringUtils.isEmpty(configuredRedirectRootUrl)) {
      redirectUri = configuredRedirectRootUrl;
    } else {
      final URI baseUri = requestContext.getUriInfo().getBaseUri();
      redirectUri = baseUri.getScheme() + "://" + baseUri.getHost();
      if (
        // value is -1 if no port is set, in that case no need to add it
        baseUri.getPort() != -1
      ) {
        redirectUri += ":" + baseUri.getPort();
      }
      redirectUri += configurationService.getContextPath().orElse("");
    }
    log.trace("Using root redirect Url: {}", redirectUri);
    return redirectUri;
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.CCSMTokenService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.net.URI;

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
    final Tokens tokens = ccsmTokenService.exchangeAuthCode(authCode, requestContext.getUriInfo().getAbsolutePath().toString());
    final AccessToken accessToken;
    try {
      accessToken = ccsmTokenService.verifyToken(tokens.getAccessToken());
    } catch (NotAuthorizedException ex) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("User has no authorization to access Optimize. Please check your Identity configuration")
        .build();
    }
    final Response.ResponseBuilder responseBuilder = Response.seeOther(URI.create(buildRootRedirect(requestContext)));
    ccsmTokenService.createOptimizeAuthCookies(tokens, accessToken, requestContext.getUriInfo().getRequestUri().getScheme())
      .forEach(cookie -> responseBuilder.header(HttpHeaders.SET_COOKIE, cookie));
    return responseBuilder.build();
  }

  @Override
  public Response logout(final ContainerRequestContext requestContext) {
    throw new NotSupportedException("Cannot logout in Optimize in Camunda Platform Self-Managed mode");
  }

  private String buildRootRedirect(final ContainerRequestContext requestContext) {
    final URI baseUri = requestContext.getUriInfo().getBaseUri();
    String redirectUri = baseUri.getScheme() + "://" + baseUri.getHost();
    if (
      // value is -1 if no port is set, in that case no need to add it
      baseUri.getPort() != -1
    ) {
      redirectUri += ":" + baseUri.getPort();
    }
    redirectUri += configurationService.getContextPath().orElse("");
    return redirectUri;
  }

}

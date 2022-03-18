/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.AccessToken;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.net.URI;

@Component
@Conditional(CCSMCondition.class)
@Slf4j
public class CCSMAuthenticationService extends AbstractAuthenticationService {

  private static final String OPTIMIZE_PERMISSION = "write:*";

  private final Identity identity;

  public CCSMAuthenticationService(final Identity identity, final SessionService sessionService,
                                   final AuthCookieService authCookieService) {
    super(sessionService, authCookieService);
    this.identity = identity;
  }

  @Override
  public Response authenticateUser(final ContainerRequestContext requestContext,
                                   final CredentialsRequestDto credentials) {
    throw new NotSupportedException("Requests to this endpoint are not valid in CCSM mode");
  }

  @Override
  public Response loginCallback(final ContainerRequestContext requestContext,
                                final AuthCodeDto authCode) {
    final Tokens tokens = identity.authentication()
      .exchangeAuthCode(authCode, requestContext.getUriInfo().getAbsolutePath().toString());

    final AccessToken accessToken = identity.authentication().verifyToken(tokens.getAccessToken());
    if (!userHasOptimizeAuthorization(accessToken)) {
      return Response.status(Response.Status.FORBIDDEN)
        .entity("User has no authorization to access Optimize. Please check your Identity configuration")
        .build();
    }
    final String securityToken =
      sessionService.createAuthToken(accessToken.getUserDetails().getName().orElse(""));
    return Response.seeOther(URI.create(buildRootRedirect(requestContext)))
      .entity(securityToken)
      .header(
        HttpHeaders.SET_COOKIE,
        authCookieService.createNewOptimizeAuthCookie(
          securityToken,
          requestContext.getUriInfo().getRequestUri().getScheme()
        )
      )
      .build();
  }

  @Override
  public Response logout(final ContainerRequestContext requestContext) {
    throw new NotSupportedException("Cannot logout in Optimize in CCSM mode");
  }

  private static String buildRootRedirect(final ContainerRequestContext requestContext) {
    final URI baseUri = requestContext.getUriInfo().getBaseUri();
    String redirectUri = baseUri.getScheme() + "://" + baseUri.getHost();
    if ((baseUri.getScheme().equals("http") && baseUri.getPort() != 80) || (
      baseUri.getScheme().equals("https") && baseUri.getPort() != 443)) {
      redirectUri += ":" + baseUri.getPort();
    }
    return redirectUri;
  }

  private boolean userHasOptimizeAuthorization(final AccessToken accessToken) {
    return accessToken.getPermissions().contains(OPTIMIZE_PERMISSION);
  }

}

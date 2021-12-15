/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.authentication.Tokens;
import io.camunda.iam.sdk.authentication.UserInfo;
import io.camunda.iam.sdk.authentication.dto.AuthCodeDto;
import io.camunda.iam.sdk.authentication.dto.LogoutRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Optional;

@Component
@Conditional(CCSMCondition.class)
@Slf4j
public class CCSMAuthenticationService extends AbstractAuthenticationService {

  private final IamApi iamApi;

  public CCSMAuthenticationService(final IamApi iamApi, final SessionService sessionService,
                                   final AuthCookieService authCookieService) {
    super(sessionService, authCookieService);
    this.iamApi = iamApi;
  }

  @Override
  public Response authenticateUser(final ContainerRequestContext requestContext,
                                   final CredentialsRequestDto credentials) {
    throw new NotSupportedException("Requests to this endpoint are not valid in CCSM mode");
  }

  @Override
  public Response loginCallback(final ContainerRequestContext requestContext,
                                final AuthCodeDto authCode) {
    final Tokens tokens = iamApi.authentication()
      .exchangeAuthCode(authCode, requestContext.getUriInfo().getAbsolutePath().toString());
    final UserInfo userInfo = iamApi.authentication().userInfo(tokens);
    final String securityToken = sessionService.createAuthToken(userInfo.getFullName());
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
  public Response logoutCallback(final ContainerRequestContext requestContext,
                                 final LogoutRequestDto logoutRequestDto) {
    final Optional<String> redirectUri = iamApi
      .authentication()
      .getLogoutRequestRedirectUri(logoutRequestDto);
    sessionService.invalidateSession(requestContext);
    return Response.seeOther(URI.create(redirectUri.orElse(buildRootRedirect(requestContext))))
      .cookie(authCookieService.createDeleteOptimizeAuthCookie(requestContext.getUriInfo().getRequestUri().getScheme()))
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

}

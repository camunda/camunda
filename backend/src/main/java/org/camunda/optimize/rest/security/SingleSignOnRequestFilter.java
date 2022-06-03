/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.AuthenticationExtractorProvider;
import org.camunda.optimize.plugin.security.authentication.AuthenticationExtractor;
import org.camunda.optimize.plugin.security.authentication.AuthenticationResult;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

import static org.camunda.optimize.rest.constants.RestConstants.CACHE_CONTROL_NO_STORE;
import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;

@AllArgsConstructor
@Slf4j
public class SingleSignOnRequestFilter extends GenericFilterBean {
  private final AuthenticationExtractorProvider authenticationExtractorProvider;
  private final ApplicationAuthorizationService applicationAuthorizationService;
  private final SessionService sessionService;
  private final AuthCookieService authCookieService;

  /**
   * Before the user can access the login page it is possible that
   * plugins were defined to perform a custom authentication check, e.g.
   * by reading the request headers. That allows the user to add the
   * single sign on functionality to Optimize.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    log.debug("Received new request.");
    HttpServletResponse servletResponse = (HttpServletResponse) response;
    HttpServletRequest servletRequest = (HttpServletRequest) request;

    if (authenticationExtractorProvider.hasPluginsConfigured()) {
      servletResponse.addHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_NO_STORE);
      provideAuthentication(servletResponse, servletRequest);
    }

    chain.doFilter(request, response);
  }

  private void provideAuthentication(HttpServletResponse servletResponse, HttpServletRequest servletRequest) {
    boolean hasValidSession = sessionService.hasValidSession(servletRequest);
    if (!hasValidSession) {
      log.debug("Creating new auth header for the Optimize cookie.");
      addTokenFromAuthenticationExtractorPlugins(servletRequest, servletResponse);
    }
  }

  private void addTokenFromAuthenticationExtractorPlugins(HttpServletRequest servletRequest,
                                                          HttpServletResponse servletResponse) {
    for (AuthenticationExtractor plugin : authenticationExtractorProvider.getPlugins()) {
      final AuthenticationResult authenticationResult = plugin.extractAuthenticatedUser(servletRequest);
      if (authenticationResult.isAuthenticated()) {
        log.debug("User [{}] could be authenticated.", authenticationResult.getAuthenticatedUser());
        final String userId = authenticationResult.getAuthenticatedUser();
        createSessionIfIsAuthorizedToAccessOptimize(servletRequest, servletResponse, userId);
        break;
      }
    }
  }

  private void createSessionIfIsAuthorizedToAccessOptimize(HttpServletRequest servletRequest,
                                                           HttpServletResponse servletResponse,
                                                           String userId) {
    boolean isAuthorized = applicationAuthorizationService.isUserAuthorizedToAccessOptimize(userId);
    if (isAuthorized) {
      log.debug("User [{}] was authorized to access Optimize, creating new session token.", userId);
      String securityToken = sessionService.createAuthToken(userId);
      authorizeCurrentRequest(servletRequest, securityToken);
      writeOptimizeAuthorizationCookieToResponse(servletRequest, servletResponse, securityToken);
    }
  }

  private void authorizeCurrentRequest(final HttpServletRequest servletRequest, final String token) {
    final String optimizeAuthToken = AuthCookieService.createOptimizeAuthCookieValue(token);
    // for direct access by request filters
    servletRequest.setAttribute(OPTIMIZE_AUTHORIZATION, optimizeAuthToken);
  }

  private void writeOptimizeAuthorizationCookieToResponse(final HttpServletRequest servletRequest,
                                                          final HttpServletResponse servletResponse,
                                                          final String token) {
    final String optimizeAuthCookie = authCookieService.createNewOptimizeAuthCookie(token, servletRequest.getScheme());
    servletResponse.addHeader(HttpHeaders.SET_COOKIE, optimizeAuthCookie);
  }

}

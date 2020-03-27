/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import org.camunda.optimize.plugin.AuthenticationExtractorProvider;
import org.camunda.optimize.plugin.security.authentication.AuthenticationExtractor;
import org.camunda.optimize.plugin.security.authentication.AuthenticationResult;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

import static org.camunda.optimize.service.security.AuthCookieService.OPTIMIZE_AUTHORIZATION;

public class SingleSignOnFilter implements Filter {

  private static final String NO_STORE = "no-store";
  private static final Logger logger = LoggerFactory.getLogger(SingleSignOnFilter.class);
  private SpringAwareServletConfiguration awareDelegate;

  private AuthenticationExtractorProvider authenticationExtractorProvider;
  private EngineContextFactory engineContextFactory;
  private ApplicationAuthorizationService applicationAuthorizationService;
  private SessionService sessionService;
  private AuthCookieService authCookieService;


  public SingleSignOnFilter(SpringAwareServletConfiguration awareDelegate) {
    this.awareDelegate = awareDelegate;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    // nothing to do here
  }

  /**
   * Before the user can access the login page it is possible that
   * plugins were defined to perform a custom authentication check, e.g.
   * by reading the request headers. That allows the user to add the
   * single sign on functionality to Optimize.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException {
    logger.debug("Received new request.");
    initBeans();
    HttpServletResponse servletResponse = (HttpServletResponse) response;
    HttpServletRequest servletRequest = (HttpServletRequest) request;

    if (authenticationExtractorProvider.hasPluginsConfigured()) {
      servletResponse.addHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
      provideAuthentication(servletResponse, servletRequest);
    }

    chain.doFilter(request, response);
  }

  private void initBeans() {
    if (authenticationExtractorProvider == null) {
      authenticationExtractorProvider = awareDelegate.getApplicationContext()
        .getBean(AuthenticationExtractorProvider.class);
    }
    if (engineContextFactory == null) {
      engineContextFactory = awareDelegate.getApplicationContext().getBean(EngineContextFactory.class);
    }
    if (applicationAuthorizationService == null) {
      applicationAuthorizationService = awareDelegate.getApplicationContext()
        .getBean(ApplicationAuthorizationService.class);
    }
    if (sessionService == null) {
      sessionService = awareDelegate.getApplicationContext().getBean(SessionService.class);
    }
    if (authCookieService == null) {
      authCookieService = awareDelegate.getApplicationContext().getBean(AuthCookieService.class);
    }
  }

  private void provideAuthentication(HttpServletResponse servletResponse, HttpServletRequest servletRequest) {
    boolean hasValidSession = sessionService.hasValidSession(servletRequest);
    if (!hasValidSession) {
      logger.debug("Creating new auth header for the Optimize cookie.");
      addTokenFromAuthenticationExtractorPlugins(servletRequest, servletResponse);
    }
  }

  private void addTokenFromAuthenticationExtractorPlugins(HttpServletRequest servletRequest,
                                                          HttpServletResponse servletResponse) {
    for (AuthenticationExtractor plugin : authenticationExtractorProvider.getPlugins()) {
      final AuthenticationResult authenticationResult = plugin.extractAuthenticatedUser(servletRequest);
      if (authenticationResult.isAuthenticated()) {
        logger.debug("User [{}] could be authenticated.", authenticationResult.getAuthenticatedUser());
        final String userName = authenticationResult.getAuthenticatedUser();
        createSessionIfIsAuthorizedToAccessOptimize(servletRequest, servletResponse, userName);
        break;
      }
    }
  }

  private void createSessionIfIsAuthorizedToAccessOptimize(HttpServletRequest servletRequest,
                                                           HttpServletResponse servletResponse,
                                                           String userName) {
    boolean isAuthorized = applicationAuthorizationService.isUserAuthorizedToAccessOptimize(userName);
    if (isAuthorized) {
      logger.debug("User [{}] was authorized to access Optimize, creating new session token.", userName);
      String securityToken = sessionService.createAuthToken(userName);
      authorizeCurrentRequest(servletRequest, securityToken);
      writeOptimizeAuthorizationCookieToResponse(servletResponse, securityToken);
    }
  }

  private void authorizeCurrentRequest(final HttpServletRequest servletRequest, final String token) {
    final String optimizeAuthToken = AuthCookieService.createOptimizeAuthCookieValue(token);
    // for direct access by request filters
    servletRequest.setAttribute(OPTIMIZE_AUTHORIZATION, optimizeAuthToken);
  }

  private void writeOptimizeAuthorizationCookieToResponse(final HttpServletResponse servletResponse,
                                                          final String token) {
    final String optimizeAuthCookie = authCookieService.createNewOptimizeAuthCookie(token);
    servletResponse.addHeader(HttpHeaders.SET_COOKIE, optimizeAuthCookie);
  }

  @Override
  public void destroy() {
    // nothing to do here
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.SSO_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;
import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_CALLBACK;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile(SSO_AUTH_PROFILE)
public class SSOController {

  private static final Logger LOGGER = LoggerFactory.getLogger(SSOController.class);

  @Autowired private Auth0Service auth0Service;

  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  /**
   * login the user - the user authentication will be delegated to auth0
   *
   * @return a redirect command to auth0 authorize url
   */
  @RequestMapping(
      value = LOGIN_RESOURCE,
      method = {RequestMethod.GET, RequestMethod.POST})
  public String login(final HttpServletRequest req, final HttpServletResponse res) {
    final String authorizeUrl = auth0Service.getAuthorizeUrl(req, res);
    LOGGER.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  @GetMapping(value = SSO_CALLBACK)
  public void loggedInCallback(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    LOGGER.debug(
        "Called back by auth0 with {} {} and SessionId: {}",
        req.getRequestURI(),
        req.getQueryString(),
        req.getSession().getId());
    try {
      final var authentication = auth0Service.authenticate(req, res);

      final var context = securityContextHolderStrategy.createEmptyContext();
      context.setAuthentication(authentication);
      securityContextHolderStrategy.setContext(context);
      securityContextRepository.saveContext(context, req, res);

      sessionExpiresWhenAuthenticationExpires(req);

      redirectToPage(req, res);
    } catch (final Exception t) {
      // removed logout, if no permission, user shouldn't be logged out from cloud
      clearContextAndRedirectToNoPermission(req, res, t);
    }
  }

  private void redirectToPage(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    final Object originalRequestUrl = req.getSession().getAttribute(REQUESTED_URL);
    if (originalRequestUrl != null) {
      res.sendRedirect(originalRequestUrl.toString());
    } else {
      res.sendRedirect(ROOT);
    }
  }

  /** Is called when there was an in authentication or authorization */
  @RequestMapping(value = NO_PERMISSION)
  @ResponseBody
  public String noPermissions() {
    return "No permission for Tasklist - Please check your Tasklist configuration or cloud configuration.";
  }

  /** Logout - Invalidates session and logout from auth0, after that redirects to root url. */
  @RequestMapping(value = LOGOUT_RESOURCE)
  public void logout(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    LOGGER.debug("logout user");
    cleanup(req);
    logoutFromAuth0(res, auth0Service.getRedirectURI(req, ROOT));
  }

  protected void clearContextAndRedirectToNoPermission(
      final HttpServletRequest req, final HttpServletResponse res, final Throwable t)
      throws IOException {
    LOGGER.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(req.getContextPath() + NO_PERMISSION);
  }

  protected void logoutAndRedirectToNoPermissionPage(
      final HttpServletRequest req, final HttpServletResponse res) throws IOException {
    LOGGER.error("User is authenticated but there are no permissions. Show noPermission message");
    cleanup(req);
    logoutFromAuth0(res, auth0Service.getRedirectURI(req, NO_PERMISSION));
  }

  protected void cleanup(final HttpServletRequest req) {
    req.getSession().invalidate();

    final var context = securityContextHolderStrategy.getContext();
    if (context != null) {
      context.setAuthentication(null);
      securityContextHolderStrategy.clearContext();
    }
  }

  protected void logoutFromAuth0(final HttpServletResponse res, final String returnTo)
      throws IOException {
    res.sendRedirect(auth0Service.getLogoutUrlFor(returnTo));
  }

  private void sessionExpiresWhenAuthenticationExpires(final HttpServletRequest req) {
    req.getSession().setMaxInactiveInterval(-1);
  }
}

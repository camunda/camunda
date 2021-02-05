/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.security.sso;

import static io.zeebe.tasklist.webapp.security.TasklistURIs.CALLBACK_URI;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.LOGOUT_RESOURCE;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.ROOT;
import static io.zeebe.tasklist.webapp.security.TasklistURIs.SSO_AUTH_PROFILE;

import com.auth0.AuthenticationController;
import com.auth0.Tokens;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile(SSO_AUTH_PROFILE)
public class SSOController {

  private static final Logger LOGGER = LoggerFactory.getLogger(SSOController.class);

  @Autowired protected SSOWebSecurityConfig config;

  @Autowired private BeanFactory beanFactory;

  @Autowired private AuthenticationController authenticationController;

  /**
   * login the user - the user authentication will be delegated to auth0
   *
   * @return a redirect command to auth0 authorize url
   */
  @RequestMapping(
      value = LOGIN_RESOURCE,
      method = {RequestMethod.GET, RequestMethod.POST})
  public String login(final HttpServletRequest req, final HttpServletResponse res) {
    final String authorizeUrl = getAuthorizeUrl(req, res);
    LOGGER.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  private String getAuthorizeUrl(final HttpServletRequest req, final HttpServletResponse res) {
    return authenticationController
        .buildAuthorizeUrl(req, res, getRedirectURI(req, CALLBACK_URI))
        .withAudience(
            String.format("https://%s/userinfo", config.getBackendDomain())) // get user profile
        .withScope("openid profile email") // which info we request
        .build();
  }

  /**
   * Logged in callback - Is called by auth0 with results of user authentication (GET) <br>
   * Redirects to root url if successful, otherwise it will redirected to an error url.
   */
  @RequestMapping(value = CALLBACK_URI, method = RequestMethod.GET)
  public void loggedInCallback(final HttpServletRequest req, final HttpServletResponse res)
      throws IOException {
    LOGGER.debug("Called back by auth0 with {} {}", req.getRequestURI(), req.getQueryString());
    try {
      final Tokens tokens = authenticationController.handle(req, res);
      final TokenAuthentication authentication = beanFactory.getBean(TokenAuthentication.class);
      authentication.authenticate(tokens);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      final Object originalRequestUrl =
          req.getSession().getAttribute(SSOWebSecurityConfig.REQUESTED_URL);
      if (originalRequestUrl != null) {
        res.sendRedirect(originalRequestUrl.toString());
      } else {
        res.sendRedirect(ROOT);
      }
    } catch (InsufficientAuthenticationException iae) {
      logoutAndRedirectToNoPermissionPage(req, res);
    } catch (Exception t /*AuthenticationException | IdentityVerificationException e*/) {
      clearContextAndRedirectToNoPermission(req, res, t);
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
  public void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
    LOGGER.debug("logout user");
    cleanup(req);
    logoutFromAuth0(res, getRedirectURI(req, ROOT));
  }

  protected void clearContextAndRedirectToNoPermission(
      HttpServletRequest req, HttpServletResponse res, Throwable t) throws IOException {
    LOGGER.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(NO_PERMISSION);
  }

  protected void logoutAndRedirectToNoPermissionPage(
      HttpServletRequest req, HttpServletResponse res) throws IOException {
    LOGGER.error("User is authenticated but there are no permissions. Show noPermission message");
    cleanup(req);
    logoutFromAuth0(res, getRedirectURI(req, NO_PERMISSION));
  }

  protected void cleanup(HttpServletRequest req) {
    req.getSession().invalidate();
    SecurityContextHolder.clearContext();
  }

  public String getLogoutUrlFor(String returnTo) {
    return String.format(
        "https://%s/v2/logout?client_id=%s&returnTo=%s",
        config.getDomain(), config.getClientId(), returnTo);
  }

  protected void logoutFromAuth0(HttpServletResponse res, String returnTo) throws IOException {
    res.sendRedirect(getLogoutUrlFor(returnTo));
  }

  protected String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80)
        || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    final String clusterId = req.getContextPath().replaceAll("/", "");
    final String result =
        redirectUri + /* req.getContextPath()+ */ redirectTo + "?uuid=" + clusterId;
    LOGGER.debug("RedirectURI: {}", result);
    return result;
  }
}

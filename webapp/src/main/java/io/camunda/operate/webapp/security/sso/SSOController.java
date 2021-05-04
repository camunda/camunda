/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.webapp.security.OperateURIs.CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_AUTH_PROFILE;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.camunda.operate.property.OperateProperties;
import javax.servlet.http.HttpSession;

import io.camunda.operate.util.RetryOperation;
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

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  public static final String ACCESS_TOKENS = "access_tokens";

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private AuthenticationController authenticationController;

  /**
   * login the user - the user authentication will be delegated to auth0
   * @param req
   * @return a redirect command to auth0 authorize url
   */
  @RequestMapping(value = LOGIN_RESOURCE, method = { RequestMethod.GET, RequestMethod.POST })
  public String login(final HttpServletRequest req,final HttpServletResponse res) throws IOException {
    final String authorizeUrl = getAuthorizeUrl(req, res);
    logger.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  private String getAuthorizeUrl(final HttpServletRequest req, final HttpServletResponse res) {
    return authenticationController
        .buildAuthorizeUrl(req, res, getRedirectURI(req, CALLBACK_URI))
        .withAudience(
            String.format("https://%s/userinfo", operateProperties.getAuth0().getBackendDomain())) // get user profile
        .withScope("openid profile email") // which info we request
        .build();
  }

  /**
   * Logged in callback -  Is called by auth0 with results of user authentication (GET) <br/>
   * Redirects to root url if successful, otherwise it will redirected to an error url.
   * @param req
   * @param res
   * @throws ServletException
   * @throws IOException
   */
  @RequestMapping(value = CALLBACK_URI, method = RequestMethod.GET)
  public void loggedInCallback(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
    logger.debug("Called back by auth0 with {} {} and SessionId: {}", req.getRequestURI(), req.getQueryString(), req.getSession().getId());
    try {
      Tokens tokens = RetryOperation.<Tokens>newBuilder()
          .noOfRetry(10)
          .delayInterval(500, TimeUnit.MILLISECONDS)
          .retryOn(IdentityVerificationException.class)
          .retryConsumer(() -> retrieveToken(req, res))
          .build()
          .retry();
      authenticate(tokens);
      saveTokens(req, tokens);
      redirectToPage(req, res);
    } catch (InsufficientAuthenticationException iae) {
      logoutAndRedirectToNoPermissionPage(req, res);
    } catch (Exception t /*AuthenticationException | IdentityVerificationException e*/) {
      clearContextAndRedirectToNoPermission(req,res, t);
    }
  }

  private Tokens retrieveToken(final HttpServletRequest request, final HttpServletResponse response) throws IdentityVerificationException {
    Tokens tokens = (Tokens) request.getSession().getAttribute(ACCESS_TOKENS);
    if( tokens == null) {
      tokens = authenticationController.handle(request, response);
    }
    return tokens;
  }

  private boolean authenticate(final Tokens tokens) {
    TokenAuthentication authentication =  beanFactory.getBean(TokenAuthentication.class);
    authentication.authenticate(tokens);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return authentication.isAuthenticated();
  }

  private void redirectToPage(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
    Object originalRequestUrl = req.getSession().getAttribute(SSOWebSecurityConfig.REQUESTED_URL);
    if (originalRequestUrl != null) {
        res.sendRedirect(originalRequestUrl.toString());
    } else {
        res.sendRedirect(ROOT);
    }
  }

  private void saveTokens(final HttpServletRequest req,final Tokens tokens) {
    HttpSession httpSession = req.getSession();
    httpSession.setMaxInactiveInterval(Long.valueOf(tokens.getExpiresIn()).intValue());
    httpSession.setAttribute(ACCESS_TOKENS, tokens);
  }

  private Tokens readTokens(final HttpServletRequest req) {
    return (Tokens) req.getSession().getAttribute(ACCESS_TOKENS);
  }

  /**
   * Is called when there was an in authentication or authorization
   * @return
   */
  @RequestMapping(value = NO_PERMISSION)
  @ResponseBody
  public String noPermissions() {
    return "No permission for Operate - Please check your operate configuration or cloud configuration.";
  }

  /**
   * Logout - Invalidates session and logout from auth0, after that redirects to root url.
   * @param req
   * @param res
   * @throws IOException
   */
  @RequestMapping(value = LOGOUT_RESOURCE)
  public void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.debug("logout user");
    cleanup(req);
    logoutFromAuth0(res, getRedirectURI(req, ROOT));
  }

  protected void clearContextAndRedirectToNoPermission(HttpServletRequest req,HttpServletResponse res, Throwable t) throws IOException {
    logger.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(NO_PERMISSION);
  }

  protected void logoutAndRedirectToNoPermissionPage(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.warn("User is authenticated but there are no permissions. Show noPermission message");
    cleanup(req);
    logoutFromAuth0(res, getRedirectURI(req, NO_PERMISSION));
  }

  protected void cleanup(HttpServletRequest req) {
    req.getSession().invalidate();
    SecurityContextHolder.clearContext();
  }

  public String getLogoutUrlFor(String returnTo) {
    return String.format("https://%s/v2/logout?client_id=%s&returnTo=%s",
        operateProperties.getAuth0().getDomain(), operateProperties.getAuth0().getClientId(),
        returnTo);
  }

  protected void logoutFromAuth0(HttpServletResponse res, String returnTo) throws IOException {
    res.sendRedirect(getLogoutUrlFor(returnTo));
  }

  protected String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80) || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    final String clusterId = req.getContextPath().replaceAll("/", "");
    final String result =
        redirectUri + /* req.getContextPath()+ */ redirectTo + "?uuid=" + clusterId;
    logger.debug("RedirectURI: {}", result);
    return result;
  }

}

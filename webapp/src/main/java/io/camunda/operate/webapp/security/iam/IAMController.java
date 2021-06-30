/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.iam;

import static io.camunda.operate.webapp.security.OperateURIs.IAM_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.IAM_LOGOUT_CALLBACK_URI;
import static io.camunda.operate.webapp.security.OperateURIs.IAM_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.LOGOUT_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.NO_PERMISSION;
import static io.camunda.operate.webapp.security.OperateURIs.ROOT;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.authentication.dto.AuthCodeDto;
import io.camunda.iam.sdk.authentication.dto.LogoutRequestDto;
import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import java.util.Optional;
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
@Profile(IAM_AUTH_PROFILE)
public class IAMController {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  private BeanFactory beanFactory;

  @Autowired
  private IamApi iamApi;

  /**
   * Initiates user login - the user will be redirected to Camunda Account
   * @param req request
   * @return a redirect command to Camunda Account authorize url
   */
  @RequestMapping(value = LOGIN_RESOURCE, method = { RequestMethod.GET, RequestMethod.POST })
  public String login(final HttpServletRequest req) {
    final String authorizeUrl = iamApi
        .authentication()
        .authorizeUriBuilder(IAMAuthentication.getRedirectURI(req, IAM_CALLBACK_URI))
        .build()
        .toString();
    logger.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  /**
   * Logged in callback -  Is called by Camunda Account with results of user authentication (GET) <br/>
   * Redirects to root url if successful, otherwise it will redirected to an error url.
   * @param req request
   * @param res response
   * @param authCodeDto authorize response
   * @throws IOException IO Exception
   */
  @RequestMapping(value = IAM_CALLBACK_URI, method = RequestMethod.GET)
  public void loggedInCallback(final HttpServletRequest req, final HttpServletResponse res, AuthCodeDto authCodeDto) throws IOException {
    logger.debug("Called back by iam with {} {}, SessionId: {} and AuthCode {}",
        req.getRequestURI(), req.getQueryString(),
        req.getSession().getId(),
        authCodeDto.getCode());
    try {
      IAMAuthentication authentication = beanFactory.getBean(IAMAuthentication.class);
      authentication.authenticate(req, authCodeDto);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      redirectToPage(req, res);
    } catch (InsufficientAuthenticationException iae) {
      logoutAndRedirectToNoPermissionPage(req, res);
    } catch (Exception t) {
      clearContextAndRedirectToNoPermission(req, res, t);
    }
  }

  private void redirectToPage(final HttpServletRequest req, final HttpServletResponse res) throws IOException {
    Object originalRequestUrl = req.getSession().getAttribute(IAMWebSecurityConfig.REQUESTED_URL);
    if (originalRequestUrl != null) {
        res.sendRedirect(originalRequestUrl.toString());
    } else {
        res.sendRedirect(ROOT);
    }
  }

  /**
   * Is called when there was an in authentication or authorization
   * @return no permissions error message
   */
  @RequestMapping(value = NO_PERMISSION)
  @ResponseBody
  public String noPermissions() {
    return "No permission for Operate - Please check your operate configuration or cloud configuration.";
  }

  /**
   * Logout - Invalidates session and logout from auth0, after that redirects to root url.
   * @param req request
   * @param res response
   * @throws IOException exception
   */
  @RequestMapping(value = LOGOUT_RESOURCE)
  public void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.debug("logout user");
    cleanup(req);
    logoutFromIAM(res, IAMAuthentication.getRedirectURI(req, ROOT));
  }

  /**
   * Log out callback -  Is called by Camunda Account during the single sign off process<br/>
   * Redirects to provided url if successful, otherwise it will redirected to an error url.
   * @param req request
   * @param res response
   * @param logoutRequestDto IAM log out request
   * @throws IOException IO Exception
   */
  @RequestMapping(value = IAM_LOGOUT_CALLBACK_URI, method = RequestMethod.GET)
  public void logOutCallback(final HttpServletRequest req, final HttpServletResponse res, final LogoutRequestDto logoutRequestDto) throws IOException {
    logger.debug("Logout requested by iam with {} {}, SessionId: {} and AuthCode {}",
        req.getRequestURI(), req.getQueryString(),
        req.getSession().getId(),
        logoutRequestDto);
    try {
      final Optional<String> redirectUri = iamApi
          .authentication()
          .getLogoutRequestRedirectUri(logoutRequestDto);
      cleanup(req);

      if (redirectUri.isEmpty()) {
        res.sendRedirect(ROOT);
      } else {
        res.sendRedirect(redirectUri.get());
      }
    } catch (Exception t) {
      // TODO: display error?
      res.sendError(500, t.getMessage());
    }
  }

  protected void clearContextAndRedirectToNoPermission(HttpServletRequest req,HttpServletResponse res, Throwable t) throws IOException {
    logger.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(NO_PERMISSION);
  }

  protected void logoutAndRedirectToNoPermissionPage(HttpServletRequest req, HttpServletResponse res) throws IOException {
    logger.warn("User is authenticated but there are no permissions. Show noPermission message");
    cleanup(req);
    logoutFromIAM(res, IAMAuthentication.getRedirectURI(req, NO_PERMISSION));
  }

  protected void cleanup(HttpServletRequest req) {
    req.getSession().invalidate();
    SecurityContextHolder.clearContext();
  }

  protected void logoutFromIAM(HttpServletResponse res, String returnTo) throws IOException {
    final String logoutUrl = iamApi
        .authentication()
        .logoutUriBuilder()
        .withRedirectUri(returnTo)
        .build()
        .toString();

    res.sendRedirect(logoutUrl);
  }
}

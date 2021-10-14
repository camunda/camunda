/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.iam;

import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_AUTH_PROFILE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_CALLBACK_URI;
import static io.camunda.tasklist.webapp.security.TasklistURIs.IAM_LOGOUT_CALLBACK_URI;
import static io.camunda.tasklist.webapp.security.TasklistURIs.LOGIN_RESOURCE;
import static io.camunda.tasklist.webapp.security.TasklistURIs.NO_PERMISSION;
import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;
import static io.camunda.tasklist.webapp.security.TasklistURIs.ROOT;

import io.camunda.iam.sdk.IamApi;
import io.camunda.iam.sdk.authentication.dto.AuthCodeDto;
import io.camunda.iam.sdk.authentication.dto.LogoutRequestDto;
import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile(IAM_AUTH_PROFILE)
public class IAMController {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private IamApi iamApi;

  /**
   * Initiates user login - the user will be redirected to Camunda Account
   *
   * @param req request
   * @return a redirect command to Camunda Account authorize url
   */
  @GetMapping(LOGIN_RESOURCE)
  public String login(final HttpServletRequest req) {
    final String authorizeUrl =
        iamApi
            .authentication()
            .authorizeUriBuilder(IAMAuthentication.getRedirectURI(req, IAM_CALLBACK_URI))
            .build()
            .toString();
    logger.debug("Redirect Login to {}", authorizeUrl);
    return "redirect:" + authorizeUrl;
  }

  /**
   * Logged in callback - Is called by Camunda Account with results of user authentication (GET)
   * <br>
   * Redirects to root url if successful, otherwise it will redirected to an error url.
   *
   * @param req request
   * @param res response
   * @param authCodeDto authorize response
   * @throws IOException IO Exception
   */
  @GetMapping(value = IAM_CALLBACK_URI)
  public void loggedInCallback(
      final HttpServletRequest req, final HttpServletResponse res, AuthCodeDto authCodeDto)
      throws IOException {
    logger.debug(
        "Called back by iam with {} {}, SessionId: {} and AuthCode {}",
        req.getRequestURI(),
        req.getQueryString(),
        req.getSession().getId(),
        authCodeDto.getCode());
    try {
      final IAMAuthentication authentication = beanFactory.getBean(IAMAuthentication.class);
      authentication.authenticate(req, authCodeDto);
      SecurityContextHolder.getContext().setAuthentication(authentication);
      redirectToPage(req, res);
    } catch (Exception e) {
      clearContextAndRedirectToNoPermission(req, res, e);
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

  /**
   * Is called when there was an in authentication or authorization
   *
   * @return no permissions error message
   */
  @RequestMapping(value = NO_PERMISSION)
  @ResponseBody
  public String noPermissions() {
    return "No permission for Tasklist - Please check your operate configuration or cloud configuration.";
  }

  /**
   * Log out callback - Is called by Camunda Account during the single sign off process<br>
   * Redirects to provided url if successful, otherwise it will redirected to an error url.
   *
   * @param req request
   * @param res response
   * @param logoutRequestDto IAM log out request
   * @throws IOException IO Exception
   */
  @GetMapping(value = IAM_LOGOUT_CALLBACK_URI)
  public void logOutCallback(
      final HttpServletRequest req,
      final HttpServletResponse res,
      final LogoutRequestDto logoutRequestDto)
      throws IOException {
    logger.debug(
        "Logout requested by iam with {} {}, SessionId: {} and AuthCode {}",
        req.getRequestURI(),
        req.getQueryString(),
        req.getSession().getId(),
        logoutRequestDto);
    try {
      cleanup(req);
      final Optional<String> redirectUri =
          iamApi.authentication().getLogoutRequestRedirectUri(logoutRequestDto);

      if (redirectUri.isEmpty()) {
        res.sendRedirect(ROOT);
      } else {
        res.sendRedirect(redirectUri.get());
      }
    } catch (Exception t) {
      res.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), t.getMessage());
    }
  }

  protected void clearContextAndRedirectToNoPermission(
      HttpServletRequest req, HttpServletResponse res, Throwable t) throws IOException {
    logger.error("Error in authentication callback: ", t);
    cleanup(req);
    res.sendRedirect(NO_PERMISSION);
  }

  protected void cleanup(HttpServletRequest req) {
    req.getSession().invalidate();
    SecurityContextHolder.clearContext();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp;

import static io.camunda.tasklist.webapp.security.TasklistURIs.REQUESTED_URL;

import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.Arrays;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ForwardErrorController implements ErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);

  @Autowired private Environment environment;

  @RequestMapping("/error")
  public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
    final String requestedURI =
        (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (requestedURI == null) {
      return forwardToRootPage();
    }
    if (shouldSaveRequestedURI(requestedURI)) {
      return saveRequestAndRedirectToLogin(request, requestedURI);
    } else {
      return forwardToRootPage();
    }
  }

  private ModelAndView forwardToRootPage() {
    final ModelAndView modelAndView = new ModelAndView("forward:/");
    modelAndView.setStatus(HttpStatus.OK);
    return modelAndView;
  }

  private ModelAndView saveRequestAndRedirectToLogin(
      final HttpServletRequest request, final String requestedURI) {
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ",
        requestedURI,
        TasklistURIs.LOGIN_RESOURCE);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedURI);
    final ModelAndView modelAndView = new ModelAndView("redirect:" + TasklistURIs.LOGIN_RESOURCE);
    modelAndView.setStatus(HttpStatus.FOUND);
    return modelAndView;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }

  private boolean isSSOProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(TasklistURIs.SSO_AUTH_PROFILE);
  }

  private boolean isIAMProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(TasklistURIs.IAM_AUTH_PROFILE);
  }

  private boolean shouldSaveRequestedURI(String requestedURI) {
    return (isSSOProfile() || isIAMProfile())
        && !requestedURI.equals(TasklistURIs.LOGIN_RESOURCE)
        && isNotLoggedIn();
  }
}

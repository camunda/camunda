/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp;

import java.util.Arrays;
import javax.servlet.RequestDispatcher;
import io.camunda.operate.webapp.security.OperateURIs;
import io.camunda.operate.webapp.security.sso.SSOWebSecurityConfig;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class ForwardErrorController implements ErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);
  @Autowired
  private Environment environment;

  @RequestMapping(value = "/error")
  public ModelAndView handleError(HttpServletRequest request, HttpServletResponse response) {
    final String requestedURI = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
    if (isSSOProfile() && !requestedURI.equals(OperateURIs.LOGIN_RESOURCE) && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request, requestedURI);
    } else {
      if (requestedURI.contains("/api/")) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Exception exception = (Exception) request.getAttribute(
            "org.springframework.boot.web.servlet.error.DefaultErrorAttributes.ERROR");
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("message", exception != null ? exception.getMessage() : "");
        modelAndView.setStatus(HttpStatus.valueOf(statusCode));
        return modelAndView;
      }
      return forwardToRootPage(requestedURI);
    }
  }

  private ModelAndView forwardToRootPage(final String requestedURI) {
    LOGGER.warn("Requested non existing path {}. Forward (on serverside) to /\"", requestedURI);
    final ModelAndView modelAndView = new ModelAndView("forward:/");
    // Is it really necessary to set status to OK (for frontend)?
    modelAndView.setStatus(HttpStatus.OK);
    return modelAndView;
  }
  private ModelAndView saveRequestAndRedirectToLogin(
      final HttpServletRequest request, final String requestedURI) {
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ",
        requestedURI,
        OperateURIs.LOGIN_RESOURCE);
    request.getSession(true).setAttribute(SSOWebSecurityConfig.REQUESTED_URL, requestedURI);
    final ModelAndView modelAndView = new ModelAndView("redirect:" + OperateURIs.LOGIN_RESOURCE);
    modelAndView.setStatus(HttpStatus.FOUND);
    return modelAndView;
  }
  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }
  private boolean isSSOProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(OperateURIs.SSO_AUTH_PROFILE);
  }

  @Override
  public String getErrorPath() {
    return "/error";
  }
}

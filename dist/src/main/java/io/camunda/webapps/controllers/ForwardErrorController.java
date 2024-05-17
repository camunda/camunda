/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static io.camunda.application.Profile.*;
import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.REQUESTED_URL;

import io.camunda.operate.util.ConversionUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ForwardErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);

  @Autowired private Environment environment;

  @GetMapping(value = {"/{regex:[\\w-]+}", "/**/{regex:[\\w-]+}"})
  public String forward404(final HttpServletRequest request) {
    final String requestedURL = getRequestedURL(request);
    if (!requestedURL.startsWith("/tasklist") && !requestedURL.startsWith("/operate")) {
      if (environment.acceptsProfiles(TASKLIST.getId(), "!" + OPERATE.getId())) {
        return "redirect:/tasklist" + requestedURL;
      } else {
        return "redirect:/operate" + requestedURL;
      }
    } else if (isLoginDelegated() && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request, requestedURL);
    } else {
      if (requestedURL.startsWith("/tasklist")) {
        return "forward:/tasklist";
      } else {
        return "forward:/operate";
      }
    }
  }

  private String getRequestedURL(final HttpServletRequest request) {
    final String requestedUri =
        request.getRequestURI().substring(request.getContextPath().length());
    final String queryString = request.getQueryString();
    if (ConversionUtils.stringIsEmpty(queryString)) {
      return requestedUri;
    } else {
      return requestedUri + "?" + queryString;
    }
  }

  private String saveRequestAndRedirectToLogin(
      final HttpServletRequest request, final String requestedURL) {
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ", requestedURL, LOGIN_RESOURCE);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedURL);
    return "forward:" + LOGIN_RESOURCE;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }

  private boolean isLoginDelegated() {
    return Arrays.stream(environment.getActiveProfiles())
        .anyMatch(Set.of(IDENTITY_AUTH.getId(), SSO_AUTH.getId())::contains);
  }
}

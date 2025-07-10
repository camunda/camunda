/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.webapps.WebappsModuleConfiguration.WebappsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class WebappsRequestForwardManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappsRequestForwardManager.class);

  private static final String LOGIN_RESOURCE = "/login";

  @Autowired private WebappsProperties webappsProperties;

  public String forward(final HttpServletRequest request, final String app) {
    if (webappsProperties.loginDelegated() && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request);
    } else {
      return "forward:/" + app;
    }
  }

  private String saveRequestAndRedirectToLogin(final HttpServletRequest request) {
    final String requestedUrl = getRequestedUrl(request);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedUrl);
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ",
        request.getRequestURI().substring(request.getContextPath().length()),
        LOGIN_RESOURCE);
    return "forward:" + LOGIN_RESOURCE;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null
        || (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }
}

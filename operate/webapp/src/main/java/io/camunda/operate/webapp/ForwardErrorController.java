/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import static io.camunda.operate.webapp.security.OperateURIs.LOGIN_RESOURCE;
import static io.camunda.operate.webapp.security.OperateURIs.REQUESTED_URL;

import io.camunda.operate.OperateProfileService;
import io.camunda.operate.util.ConversionUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ForwardErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);

  @Autowired private OperateProfileService operateProfileService;

  @GetMapping(value = {"/{regex:[\\w-]+}", "/**/{regex:[\\w-]+}"})
  public String forward404(HttpServletRequest request) {
    final String requestedURI =
        request.getRequestURI().substring(request.getContextPath().length());
    if (operateProfileService.isLoginDelegated() && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request, requestedURI);
    } else {
      return "forward:/";
    }
  }

  private String saveRequestAndRedirectToLogin(
      final HttpServletRequest request, final String requestedURI) {
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to  {} ", requestedURI, LOGIN_RESOURCE);
    final String queryString = request.getQueryString();
    if (ConversionUtils.stringIsEmpty(queryString)) {
      request.getSession(true).setAttribute(REQUESTED_URL, requestedURI);
    } else {
      request.getSession(true).setAttribute(REQUESTED_URL, requestedURI + "?" + queryString);
    }
    return "forward:" + LOGIN_RESOURCE;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }
}

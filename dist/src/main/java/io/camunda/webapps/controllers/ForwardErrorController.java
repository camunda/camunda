/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static io.camunda.application.Profile.*;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ForwardErrorController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ForwardErrorController.class);

  private static final String LOGIN_RESOURCE = "/api/login";
  private static final String API = "/api/**";
  private static final String PUBLIC_API = "/v*/**";
  private static final String DEFAULT_MANAGEMENT_URI = "/actuator/**";
  private static final String SSO_CALLBACK_URI = "/sso-callback";
  private static final String NO_PERMISSION = "/noPermission";
  private static final String IDENTITY_CALLBACK_URI = "/identity-callback";
  private static final String REQUESTED_URL = "requestedUrl";

  @Autowired private Environment environment;

  private List<RequestMatcher> ignoredRequestsMatchers;

  @PostConstruct
  public void init() {
    // list of endpoints for which 404 is returned
    ignoredRequestsMatchers =
        List.of(
            new AntPathRequestMatcher(PUBLIC_API),
            new AntPathRequestMatcher(API),
            new AntPathRequestMatcher(NO_PERMISSION),
            new AntPathRequestMatcher(SSO_CALLBACK_URI),
            new AntPathRequestMatcher(IDENTITY_CALLBACK_URI),
            // actuator endpoints
            new AntPathRequestMatcher(
                Optional.ofNullable(environment.getProperty("management.endpoints.web.base-path"))
                    .map(s -> s.concat("/**"))
                    .orElse(DEFAULT_MANAGEMENT_URI)));
  }

  @RequestMapping(value = {"/{regex:[\\w-]+}", "/**/{regex:[\\w-]+}"})
  public String forward404(final HttpServletRequest request, final HttpServletResponse response) {
    if (ignoredRequestsMatchers.stream()
        .anyMatch(requestMatcher -> requestMatcher.matches(request))) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }
    final String requestedURL = getRequestedURL(request);
    if (!requestedURL.startsWith("/tasklist") && !requestedURL.startsWith("/operate")) {
      if (isStandaloneTasklist()) {
        return "redirect:/tasklist" + requestedURL;
      } else {
        return "redirect:/operate" + requestedURL;
      }
    } else if (isLoginDelegated() && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request, requestedURL);
    } else {
      if (isStandaloneTasklist()) {
        return "forward:/tasklist";
      } else {
        return "forward:/operate";
      }
    }
  }

  private boolean isStandaloneTasklist() {
    return environment.acceptsProfiles(TASKLIST.getId(), "!" + OPERATE.getId());
  }

  private String getRequestedURL(final HttpServletRequest request) {
    final String requestedUri =
        request.getRequestURI().substring(request.getContextPath().length());
    final String queryString = request.getQueryString();
    if (StringUtils.isEmpty(queryString)) {
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

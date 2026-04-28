/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.REQUESTED_URL;
import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@ConditionalOnWebappUiEnabled("tmp-webapp")
public class WebappIndexController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebappIndexController.class);

  private static final String LOGIN_RESOURCE = "/login";

  private final ServletContext context;

  private final boolean loginDelegated;

  public WebappIndexController(
      final ServletContext context,
      @Value("${camunda.webapps.login-delegated:false}") final boolean loginDelegated) {
    this.context = context;
    this.loginDelegated = loginDelegated;
  }

  @GetMapping({"/webapp", "/webapp/", "/webapp/index.html"})
  public String webapp(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/webapp/");
    return "webapp/index";
  }

  /**
   * Forwards SPA routes to index.html, excluding static assets.
   *
   * <p>The regex pattern uses negative lookahead to prevent matching paths starting with "assets":
   *
   * <ul>
   *   <li>{@code (?!assets)} - excludes "assets"
   *   <li>{@code .*} - matches any other path segment
   * </ul>
   *
   * <p>This exclusion is necessary because PathPatternParser (Spring Framework 6+) gives controller
   * mappings higher precedence than static resource handlers. Without this pattern, requests like
   * {@code /webapp/assets/index.css} would be forwarded to index.html instead of being served as
   * static files.
   *
   * <p>The forward + login-redirect logic is intentionally inlined here rather than reusing the
   * legacy {@code WebappsRequestForwardManager} from {@code dist/}. Once Tasklist/Operate/Admin
   * have all been migrated onto this BFF, the legacy manager and its consumers in {@code dist/} are
   * deleted, leaving this controller as the sole implementation. Bounded duplication during the
   * migration window is an explicit choice.
   */
  @RequestMapping(value = {"/webapp/{path:^(?!assets).*}", "/webapp/{path:^(?!assets).*}/**"})
  public String forwardToWebapp(final HttpServletRequest request) {
    if (loginDelegated && isNotLoggedIn()) {
      return saveRequestAndRedirectToLogin(request);
    } else {
      return "forward:/webapp";
    }
  }

  private String saveRequestAndRedirectToLogin(final HttpServletRequest request) {
    final String requestedUrl = getRequestedUrl(request);
    request.getSession(true).setAttribute(REQUESTED_URL, requestedUrl);
    LOGGER.warn(
        "Requested path {}, but not authenticated. Redirect to {}",
        request.getRequestURI().substring(request.getContextPath().length()),
        LOGIN_RESOURCE);
    return "forward:" + LOGIN_RESOURCE;
  }

  private boolean isNotLoggedIn() {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return (authentication instanceof AnonymousAuthenticationToken)
        || !authentication.isAuthenticated();
  }
}

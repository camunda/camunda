/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class IdentityIndexController {

  private final ServletContext context;

  private final WebappsRequestForwardManager webappsRequestForwardManager;

  public IdentityIndexController(
      final ServletContext context,
      final WebappsRequestForwardManager webappsRequestForwardManager) {
    this.context = context;
    this.webappsRequestForwardManager = webappsRequestForwardManager;
  }

  @GetMapping("/identity")
  public String identity(final Model model) throws IOException {
    model.addAttribute("contextPath", context.getContextPath() + "/identity/");
    return "identity/index";
  }

  /**
   * Forwards SPA routes to index.html, excluding static assets.
   *
   * <p>The regex pattern uses negative lookahead to prevent matching paths starting with "assets":
   *
   * <ul>
   *   <li>{@code (?!assets%2F?)} - excludes "assets" optionally followed by a slash (escaped as
   *       %2F)
   *   <li>{@code .*} - matches any other path segment
   * </ul>
   *
   * <p>The slash must be escaped as {@code %2F} because PathPatternParser does not allow literal
   * slashes within path variable regex patterns.
   *
   * <p>This exclusion is necessary because PathPatternParser (Spring Framework 6+) gives controller
   * mappings higher precedence than static resource handlers. Without this pattern, requests like
   * {@code /operate/assets/index.css} would be forwarded to index.html instead of being served as
   * static files.
   */
  @RequestMapping("/identity/{path:^(?!assets%2F?).*}")
  public String forwardToIdentity(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "identity");
  }
}

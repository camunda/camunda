/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@ConditionalOnWebappUiEnabled({"identity", "admin"})
public class AdminIndexController {
  private final ServletContext context;

  private final WebappsRequestForwardManager webappsRequestForwardManager;

  public AdminIndexController(
      final ServletContext context,
      final WebappsRequestForwardManager webappsRequestForwardManager) {
    this.context = context;
    this.webappsRequestForwardManager = webappsRequestForwardManager;
  }

  @GetMapping({"/admin", "/admin/", "/admin/index.html"})
  public String admin(final Model model) throws IOException {
    model.addAttribute("contextPath", context.getContextPath() + "/admin/");
    return "admin/index";
  }

  /**
   * Tenant-aware login picker URL.
   *
   * <p>Always forwards to the SPA shell, skipping {@link WebappsRequestForwardManager}'s
   * login-delegate redirect. The picker page must be reachable for unauthenticated users —
   * otherwise the user gets bounced to {@code /login} (Spring's default chooser, no tenant
   * context) instead of seeing the tenant-aware picker.
   */
  @GetMapping("/admin/{tenantId}/login")
  public String tenantPicker(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/admin/");
    return "admin/index";
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
   * {@code /admin/assets/index.css} would be forwarded to index.html instead of being served as
   * static files.
   */
  @RequestMapping(value = {"/admin/{path:^(?!assets).*}", "/admin/{path:^(?!assets).*}/**"})
  public String forwardToAdmin(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "admin");
  }
}

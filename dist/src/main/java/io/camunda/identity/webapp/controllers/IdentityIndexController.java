/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

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

  /**
   * Redirects legacy /identity routes to /admin sub-path.
   *
   * <p>This redirect ensures backward compatibility for existing deployments, bookmarks, and
   * documentation that reference /identity URLs.
   *
   * <p>Uses 302 (temporary) redirect during migration period.
   *
   * <p>TODO(#44427): Consider changing to 301 (permanent) after migration stabilizes.
   *
   * <p>TODO(#44427): This can be removed after sufficient migration period (Epic #44427).
   */
  @GetMapping({"/identity", "/identity/", "/identity/index.html"})
  public String redirectIdentityRoot(final HttpServletRequest request) {
    return "redirect:/admin" + getRequestedUrl(request).replaceFirst("^/identity", "");
  }

  /**
   * Redirects all legacy /identity/* routes to /admin/*.
   *
   * <p>Excludes assets as they are handled by static resource handlers.
   *
   * <p>TODO(#44427): This can be removed after sufficient migration period (Epic #44427).
   */
  @RequestMapping(value = {"/identity/{path:^(?!assets).*}", "/identity/{path:^(?!assets).*}/**"})
  public String redirectIdentityRoutes(final HttpServletRequest request) {
    return "redirect:/admin" + getRequestedUrl(request).replaceFirst("^/identity", "");
  }

  @GetMapping({"/tst", "/tst/", "/tst/index.html"})
  public String identity(final Model model) throws IOException {
    model.addAttribute("contextPath", context.getContextPath() + "/tst/");
    return "tst/index";
  }
}

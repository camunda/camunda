/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.configuration.conditions.ConditionalOnWebappUiEnabled;
import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@ConditionalOnWebappUiEnabled("tasklist")
public class TasklistIndexController {

  @Autowired private ServletContext context;

  @Autowired private WebappsRequestForwardManager webappsRequestForwardManager;

  @GetMapping({"/tasklist", "/tasklist/", "/tasklist/index.html"})
  public String tasklist(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/tasklist/");
    return "tasklist/index";
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
   * {@code /operate/assets/index.css} would be forwarded to index.html instead of being served as
   * static files.
   */
  @RequestMapping(value = {"/tasklist/{path:^(?!assets).*}", "/tasklist/{path:^(?!assets).*}/**"})
  public String forwardToTasklist(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "tasklist");
  }

  /**
   * Redirects the old frontend routes to the /tasklist sub-path. This can be removed after the
   * creation of the auto-discovery service.
   *
   * <p>Note: /new/{segment} requires at least one path segment (e.g., /new/process_id), while /new
   * alone will return 404. This matches the legacy behavior.
   */
  @GetMapping({"/{taskId:[\\d]+}", "/processes/{segment}/start", "/new/{segment}"})
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/tasklist" + getRequestedUrl(request);
  }
}

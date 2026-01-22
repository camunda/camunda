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
   * Forwards all sub-paths under /identity to the Identity frontend. Uses {*path} syntax which is
   * compatible with PathPatternParser (Spring Framework 6+).
   */
  @RequestMapping("/identity/{*path}")
  public String forwardToIdentity(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "identity");
  }
}

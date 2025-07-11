/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class IdentityIndexController {

  private static final String VITE_IS_OIDC = "VITE_IS_OIDC";
  private static final String VITE_INTERNAL_GROUPS_ENABLED = "VITE_INTERNAL_GROUPS_ENABLED";
  private final ServletContext context;

  private final WebappsRequestForwardManager webappsRequestForwardManager;

  private final SecurityConfiguration securityConfiguration;

  public IdentityIndexController(
      final ServletContext context,
      final WebappsRequestForwardManager webappsRequestForwardManager,
      final SecurityConfiguration securityConfiguration) {
    this.context = context;
    this.webappsRequestForwardManager = webappsRequestForwardManager;
    this.securityConfiguration = securityConfiguration;
  }

  @GetMapping("/identity")
  public String identity(final Model model) throws IOException {
    model.addAttribute("contextPath", context.getContextPath() + "/identity/");
    return "identity/index";
  }

  @GetMapping("/identity/config")
  @ResponseBody
  public Map<String, String> getClientConfig() {
    return Map.of(
        VITE_IS_OIDC,
        String.valueOf(
            AuthenticationMethod.OIDC.equals(
                securityConfiguration.getAuthentication().getMethod())),
        VITE_INTERNAL_GROUPS_ENABLED,
        String.valueOf(
            securityConfiguration.getAuthentication().getOidc() == null
                || securityConfiguration.getAuthentication().getOidc().getGroupsClaim() == null));
  }

  @RequestMapping(
      value = {"/identity/", "/identity/{regex:[\\w-]+}", "/identity/**/{regex:[\\w-]+}"})
  public String forwardToIdentity(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "identity");
  }
}

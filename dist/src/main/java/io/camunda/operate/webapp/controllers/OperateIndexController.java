/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class OperateIndexController {

  private final ServletContext context;
  private final WebappsRequestForwardManager webappsRequestForwardManager;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final AuthorizationServices authorizationServices;

  public OperateIndexController(
      final ServletContext context,
      final WebappsRequestForwardManager webappsRequestForwardManager,
      final CamundaAuthenticationProvider authenticationProvider,
      final AuthorizationServices authorizationServices) {
    this.context = context;
    this.webappsRequestForwardManager = webappsRequestForwardManager;
    this.authenticationProvider = authenticationProvider;
    this.authorizationServices = authorizationServices;
  }

  @GetMapping("/operate")
  public String operate(final Model model) {
    final var hasAccessToOperate =
        authorizationServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .hasAccessToApplication("operate");

    if (hasAccessToOperate) {
      return getOperate(model);
    } else {
      // redirect to /operate/forbidden, so that the frontend
      // shows the forbidden page eventually.
      return "redirect:/operate/forbidden";
    }
  }

  @GetMapping("/operate/forbidden")
  public String forbidden(final Model model) {
    return getOperate(model);
  }

  @RequestMapping(value = {"/operate/{regex:[\\w-]+}", "/operate/**/{regex:[\\w-]+}"})
  public String forwardToOperate(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "operate");
  }

  /**
   * Redirects the old frontend routes to the /operate sub-path. This can be removed after the
   * creation of the auto-discovery service.
   */
  @GetMapping({"/processes/*", "/decisions", "/decisions/*", "/instances", "/instances/*"})
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/operate" + getRequestedUrl(request);
  }

  private String getOperate(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/operate/");
    return "operate/index";
  }
}

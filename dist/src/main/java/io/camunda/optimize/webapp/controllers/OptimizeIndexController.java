/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class OptimizeIndexController {

  @Autowired private ServletContext context;

  @Autowired private WebappsRequestForwardManager webappsRequestForwardManager;

  @GetMapping({"/optimize", "/optimize/index.html"})
  public String optimize(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/optimize/");
    return "optimize/index";
  }

  @RequestMapping(value = {"/optimize/{regex:[\\w-]+}", "/optimize/**/{regex:[\\w-]+}"})
  public String forwardToOptimize(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "optimize");
  }

  /**
   * Redirects the old frontend routes to the /optimize sub-path. This can be removed after the
   * creation of the auto-discovery service.
   */
  @GetMapping({"/{regex:[\\d]+}", "/processes/*/start", "/new/*"})
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/optimize" + getRequestedUrl(request);
  }
}

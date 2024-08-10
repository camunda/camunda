/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.controllers;

import static io.camunda.webapps.controllers.WebappsRequestForwardManager.getRequestedUrl;

import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class OperateIndexController {

  @Autowired private ServletContext context;

  @Autowired private WebappsRequestForwardManager webappsRequestForwardManager;

  @GetMapping("/operate")
  public String tasklist(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/operate/");
    return "operate/index";
  }

  @RequestMapping(value = {"/operate/{regex:[\\w-]+}", "/operate/**/{regex:[\\w-]+}"})
  public String forwardToOperate(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "operate");
  }

  /**
   * Redirects the old frontend routes to the /operate sub-path. This can be removed after the
   * creation of the auto-discovery service.
   */
  @GetMapping({"/processes/*", "/decisions", "/decisions/*"})
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/operate" + getRequestedUrl(request);
  }
}

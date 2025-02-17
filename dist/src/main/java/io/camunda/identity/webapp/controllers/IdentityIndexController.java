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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class IdentityIndexController {

  @Autowired private ServletContext context;

  @Autowired private WebappsRequestForwardManager webappsRequestForwardManager;

  @GetMapping("/identity")
  public String identity(final Model model) throws IOException {
    model.addAttribute("contextPath", context.getContextPath() + "/identity/");
    return "identity/index";
  }

  @RequestMapping(
      value = {"/identity/", "/identity/{regex:[\\w-]+}", "/identity/**/{regex:[\\w-]+}"})
  public String forwardToIdentity(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "identity");
  }
}

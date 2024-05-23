/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import io.camunda.webapps.WebappsModuleConfiguration.WebappsProperties;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
  @Autowired private ServletContext context;
  @Autowired private WebappsProperties webappsProperties;

  @GetMapping("/index.html")
  public String index() {
    return "redirect:/" + webappsProperties.defaultApp();
  }

  @GetMapping("/operate")
  public String operate(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/operate/");
    return "operate/index";
  }

  @GetMapping("/tasklist")
  public String tasklist(final Model model) {
    model.addAttribute("contextPath", context.getContextPath() + "/tasklist/");
    return "tasklist/index";
  }
}

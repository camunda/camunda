/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.webapps.WebappsModuleConfiguration.WebappsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

  @Autowired private WebappsProperties webappsProperties;

  @GetMapping(value = {"/", "/index.html"})
  public String index() {
    return "redirect:/" + webappsProperties.defaultApp();
  }

  /**
   * Redirects the old frontend routes (common tasklist and operate routes) to the default-app
   * sub-path. This can be removed after the creation of the auto-discovery service.
   */
  @GetMapping({"/processes", "/login"})
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/" + webappsProperties.defaultApp() + getRequestedUrl(request);
  }
}

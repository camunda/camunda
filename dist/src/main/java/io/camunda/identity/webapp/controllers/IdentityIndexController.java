/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.configuration.conditions.ConditionalOnWebappUiEnabled;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @deprecated please use {@link AdminIndexController} instead.
 */
@Controller
@Deprecated
@ConditionalOnWebappUiEnabled({"identity", "admin"})
public class IdentityIndexController {

  @GetMapping({"/identity", "/identity/", "/identity/index.html"})
  public String redirectIdentityRoot(final HttpServletRequest request) {
    return "redirect:/admin" + getRequestedUrl(request).replaceFirst("^/identity", "");
  }

  /**
   * Redirects all legacy /identity/* routes to /admin/*.
   *
   * <p>Excludes assets as they are handled by static resource handlers.
   */
  @RequestMapping(value = {"/identity/{path:^(?!assets).*}", "/identity/{path:^(?!assets).*}/**"})
  public String redirectIdentityRoutes(final HttpServletRequest request) {
    return "redirect:/admin" + getRequestedUrl(request).replaceFirst("^/identity", "");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnWebappUiEnabled("operate")
public class OperateIndexController {

  /**
   * Redirects the old frontend routes to the /operate sub-path. This can be removed after the
   * creation of the auto-discovery service.
   *
   * <p>Note: Patterns like /processes/{segment} require at least one path segment (e.g.,
   * /processes/123), while /processes alone will return 404. This matches the legacy behavior.
   */
  @GetMapping({
    "/processes/{segment}",
    "/decisions",
    "/decisions/{segment}",
    "/instances",
    "/instances/{segment}"
  })
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/operate" + getRequestedUrl(request);
  }
}

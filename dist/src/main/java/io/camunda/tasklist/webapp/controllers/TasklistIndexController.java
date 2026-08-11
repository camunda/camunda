/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.controllers;

import static io.camunda.webapps.util.HttpUtils.getRequestedUrl;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnWebappUiEnabled("tasklist")
public class TasklistIndexController {

  /**
   * Redirects the old frontend routes to the /tasklist sub-path. This can be removed after the
   * creation of the auto-discovery service.
   *
   * <p>Note: /new/{segment} requires at least one path segment (e.g., /new/process_id), while /new
   * alone will return 404. This matches the legacy behavior.
   */
  @GetMapping({"/{taskId:[\\d]+}", "/processes/{segment}/start", "/new/{segment}"})
  public String redirectOldRoutes(final HttpServletRequest request) {
    return "redirect:/tasklist" + getRequestedUrl(request);
  }
}

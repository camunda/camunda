/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @deprecated please use {@link AdminClientConfigController} instead.
 */
@Controller
@Deprecated
public class IdentityClientConfigController {

  /**
   * Redirects legacy /identity/config.js to /admin/config.js.
   *
   * <p>TODO(#44427): This can be removed after sufficient migration period (Epic #44427).
   */
  @GetMapping(path = "/identity/config.js")
  @Hidden
  public String redirectToAdminConfig() {
    return "redirect:/admin/config.js";
  }
}

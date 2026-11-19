/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapp.controllers;

import io.camunda.spring.utils.ConditionalOnWebappUiEnabled;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnWebappUiEnabled("operate")
@Conditional(WebappOperateIndexController.UnifiedUiEnabled.class)
public class WebappOperateIndexController {

  private final WebappIndexController indexController;

  public WebappOperateIndexController(final WebappIndexController indexController) {
    this.indexController = indexController;
  }

  @GetMapping({"/operate", "/operate/", "/operate/index.html"})
  public String webapp(final Model model) {
    return indexController.webapp(model);
  }

  @GetMapping(
      value = {
        "/operate/{path:^(?!" + WebappIndexController.NON_SPA_SEGMENTS + "$).+}",
        "/operate/{path:^(?!" + WebappIndexController.NON_SPA_SEGMENTS + "$).+}/**"
      })
  public String forwardToWebapp(final HttpServletRequest request) {
    return indexController.forwardToShell(request, "/operate/");
  }

  static class UnifiedUiEnabled implements Condition {

    @Override
    public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
      return context
          .getEnvironment()
          .getProperty("camunda.webapps.operate.unified-ui", Boolean.class, false);
    }
  }
}

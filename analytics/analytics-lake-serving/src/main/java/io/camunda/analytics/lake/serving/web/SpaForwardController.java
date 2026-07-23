/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards the React app's client-side routes to {@code index.html} so a browser refresh (or a
 * pasted deep link) on e.g. {@code /dashboards} renders the SPA instead of Spring's whitelabel 404
 * — the router takes over from the path once the bundle loads. One mapping per top-level route the
 * client's {@code App.tsx} declares, kept explicit (rather than a catch-all regex) so {@code
 * /api/**}, static assets, and genuinely unknown paths keep their normal handling.
 */
@Controller
public class SpaForwardController {

  @GetMapping({
    "/dashboards",
    "/dashboards/**",
    "/explain",
    "/explain/**",
    "/objects",
    "/objects/**",
    "/data",
    "/data/**",
    "/today",
    "/ask",
    "/processes",
    "/processes/**"
  })
  public String forwardToSpa() {
    return "forward:/index.html";
  }
}

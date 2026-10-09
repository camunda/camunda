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
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

  private static final String SEC_FETCH_MODE = "Sec-Fetch-Mode";
  private static final String NAVIGATE = "navigate";

  private final WebappsProperties webappsProperties;

  public IndexController(final WebappsProperties webappsProperties) {
    this.webappsProperties = webappsProperties;
  }

  @GetMapping(value = {"/", "/index.html"})
  public String index() {
    return "redirect:/" + webappsProperties.defaultApp();
  }

  /**
   * Redirects the old frontend route (common tasklist and operate route) to the default-app
   * sub-path. This can be removed after the creation of the auto-discovery service.
   */
  @GetMapping("/processes")
  public String redirectOldProcessesRoute(final HttpServletRequest request) {
    return "redirect:/" + webappsProperties.defaultApp() + getRequestedUrl(request);
  }

  /**
   * Serves the two callers of a {@code GET} on the login endpoint.
   *
   * <p>A browser that navigates here came from an old bookmark and expects the login screen of the
   * default app. Every other caller wants the CSRF token that the security library writes onto this
   * response, because the login {@code POST} is rejected without a token and this endpoint is the
   * only one that hands a token to a caller that has no session yet. A redirect would hide the
   * token from those callers: a browser follows it transparently and exposes only the headers of
   * the final response, which no longer carries the token.
   *
   * <p>Telling the two apart needs the Fetch Metadata request headers, so a caller that sends none
   * of them is served as the token request. A browser too old to send them (or one behind a proxy
   * that strips them) therefore gets an empty response on an old bookmark instead of the login
   * screen, which stays reachable at the login route of the app itself.
   */
  @GetMapping("/login")
  public ResponseEntity<Void> login(final HttpServletRequest request) {
    if (NAVIGATE.equals(request.getHeader(SEC_FETCH_MODE))) {
      return ResponseEntity.status(HttpStatus.FOUND)
          .location(URI.create("/" + webappsProperties.defaultApp() + getRequestedUrl(request)))
          .build();
    }
    return ResponseEntity.noContent().build();
  }
}

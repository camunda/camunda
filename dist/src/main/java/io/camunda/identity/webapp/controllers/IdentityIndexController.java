/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.AuthorizationServices;
import io.camunda.webapps.controllers.WebappsRequestForwardManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class IdentityIndexController {
  private static final String CONTENT_SECURITY_POLICY_HEADER = "Content-Security-Policy";

  private final ServletContext context;
  private final WebappsRequestForwardManager webappsRequestForwardManager;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final AuthorizationServices authorizationServices;
  private final SecurityConfiguration securityConfiguration;

  public IdentityIndexController(
      final ServletContext context,
      final WebappsRequestForwardManager webappsRequestForwardManager,
      final CamundaAuthenticationProvider authenticationProvider,
      final AuthorizationServices authorizationServices,
      final SecurityConfiguration securityConfiguration) {
    this.context = context;
    this.webappsRequestForwardManager = webappsRequestForwardManager;
    this.authenticationProvider = authenticationProvider;
    this.authorizationServices = authorizationServices;
    this.securityConfiguration = securityConfiguration;
  }

  @GetMapping("/identity")
  public String identity(final Model model, final HttpServletResponse response) throws IOException {
    final var hasAccessToIdentity =
        authorizationServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .hasAccessToApplication("identity");

    if (hasAccessToIdentity) {
      return getIdentity(model, response);
    } else {
      // redirect to /identity/forbidden, so that the frontend
      // shows the forbidden page eventually.
      return "redirect:/identity/forbidden";
    }
  }

  @GetMapping("/identity/forbidden")
  public String forbidden(final Model model, final HttpServletResponse response)
      throws IOException {
    return getIdentity(model, response);
  }

  @RequestMapping(
      value = {"/identity/", "/identity/{regex:[\\w-]+}", "/identity/**/{regex:[\\w-]+}"})
  public String forwardToIdentity(final HttpServletRequest request) {
    return webappsRequestForwardManager.forward(request, "identity");
  }

  private void setContentSecurePolicyHeader(
      final HttpServletResponse response, final String envJsNonce) {
    response.addHeader(
        CONTENT_SECURITY_POLICY_HEADER,
        "default-src 'self';"
            + "script-src 'self' 'nonce-"
            + envJsNonce
            + "';"
            + "style-src 'self' 'unsafe-inline';"
            + "frame-src 'none';"
            + "object-src 'none';"
            + "media-src 'none';");
  }

  private String generateNonce() {
    final var secureRandom = new SecureRandom();
    final var secureBytes = new byte[16];
    secureRandom.nextBytes(secureBytes);

    return Base64.getEncoder().encodeToString(secureBytes);
  }

  public String getIdentity(final Model model, final HttpServletResponse response) {
    final var envJsNonce = generateNonce();
    model.addAttribute("contextPath", context.getContextPath() + "/identity/");
    model.addAttribute("clientConfigNonce", envJsNonce);

    final var clientConfigMap =
        Map.of(
            "VITE_IS_OIDC",
            String.valueOf(
                AuthenticationMethod.OIDC.equals(
                    securityConfiguration.getAuthentication().getMethod())),
            "VITE_INTERNAL_GROUPS_ENABLED",
            String.valueOf(
                securityConfiguration.getAuthentication().getOidc() == null
                    || securityConfiguration.getAuthentication().getOidc().getGroupsClaim()
                        == null));

    model.addAttribute("clientConfig", clientConfigMap);
    setContentSecurePolicyHeader(response, envJsNonce);

    // return index; based on the request path /forbidden, the frontend
    // will open the forbidden page.
    model.addAttribute("contextPath", context.getContextPath() + "/identity/");
    return "identity/index";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Per-PT multi-IdP picker page. Spring Security's {@code DefaultLoginPageGeneratingFilter}
 * generates this page automatically for chains whose {@code loginPage} is the default {@code
 * /login} — but on PT chains the loginPage URL is PT-prefixed (set by the entry point in {@code
 * PerTenantSecurityChainFactory#webappAuthenticationEntryPoint}), so the filter drops out and we
 * render the picker ourselves.
 */
@Controller
@Profile("pt-security")
public class PhysicalTenantLoginPageController {

  private final Map<String, ClientRegistrationRepository> repositories;

  public PhysicalTenantLoginPageController(
      final Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories) {
    this.repositories = ptClientRegistrationRepositories;
  }

  @GetMapping(value = "/physical-tenant/{tenantId}/login", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String picker(@PathVariable final String tenantId) {
    final ClientRegistrationRepository repo = repositories.get(tenantId);
    if (repo == null) {
      return "<!doctype html><html><body><h1>Unknown physical tenant: "
          + escape(tenantId)
          + "</h1></body></html>";
    }
    final List<ClientRegistration> registrations = new ArrayList<>();
    if (repo instanceof final Iterable<?> iterable) {
      for (final Object obj : iterable) {
        if (obj instanceof final ClientRegistration registration) {
          registrations.add(registration);
        }
      }
    }
    final var links = new StringBuilder();
    for (final ClientRegistration registration : registrations) {
      final String href =
          "/physical-tenant/"
              + tenantId
              + "/oauth2/authorization/"
              + registration.getRegistrationId();
      final String label =
          registration.getClientName() != null
              ? registration.getClientName()
              : registration.getRegistrationId();
      links
          .append("<li><a href=\"")
          .append(href)
          .append("\">")
          .append(escape(label))
          .append("</a></li>");
    }
    return ("""
            <!doctype html>
            <html><head><meta charset="utf-8"><title>Login — %s</title>
            <style>body{font-family:sans-serif;margin:2em;max-width:40em}
            li{margin:.5em 0}
            a{display:inline-block;padding:.5em 1em;background:#eef;
              color:#225;text-decoration:none;border-radius:4px}
            a:hover{background:#dde}</style></head><body>
            <h1>Sign in to physical tenant: %s</h1>
            <p>Choose an identity provider:</p>
            <ul>%s</ul>
            </body></html>
            """)
        .formatted(escape(tenantId), escape(tenantId), links.toString());
  }

  private static String escape(final String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

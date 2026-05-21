/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Multi-IdP picker page. Two entry points:
 *
 * <ul>
 *   <li>{@code /physical-tenant/{tenantId}/login} — picker for a specific PT, listing only that
 *       tenant's assigned providers, with links that target the PT-prefixed authorization URL.
 *   <li>{@code /login} — unprefixed picker served on CSL's standard {@code OidcWebapp} chain. Lists
 *       every registration in CSL's standard {@link ClientRegistrationRepository} (i.e. every
 *       root-declared provider). Spring Security's {@code DefaultLoginPageGeneratingFilter} is not
 *       registered on the OidcWebapp chain in our setup, so we render the picker ourselves; {@code
 *       /login} is permitAll'd on that chain by CSL.
 * </ul>
 */
@Controller
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantLoginPageController {

  private final Map<String, ClientRegistrationRepository> repositories;
  private final ClientRegistrationRepository clientRegistrationRepository;

  public PhysicalTenantLoginPageController(
      final Map<String, ClientRegistrationRepository> ptClientRegistrationRepositories,
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.repositories = ptClientRegistrationRepositories;
    this.clientRegistrationRepository = clientRegistrationRepository;
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
    final List<ClientRegistration> registrations = extractRegistrations(repo);
    final String urlPrefix = "/physical-tenant/" + tenantId;
    return renderPicker("Sign in to physical tenant: " + tenantId, urlPrefix, registrations);
  }

  /**
   * Unprefixed picker on CSL's standard webapp chain. Lists every registration in the standard
   * repository — with three root-declared providers in this PoC, three options appear. Links point
   * at the unprefixed {@code /oauth2/authorization/<regId>} since that's the chain serving this
   * URL.
   */
  @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
  @ResponseBody
  public String standardPicker() {
    final List<ClientRegistration> registrations =
        extractRegistrations(clientRegistrationRepository);
    return renderPicker("Sign in", "", registrations);
  }

  private static List<ClientRegistration> extractRegistrations(
      final ClientRegistrationRepository repo) {
    final List<ClientRegistration> registrations = new ArrayList<>();
    if (repo instanceof final Iterable<?> iterable) {
      for (final Object obj : iterable) {
        if (obj instanceof final ClientRegistration registration) {
          registrations.add(registration);
        }
      }
    }
    return registrations;
  }

  private static String renderPicker(
      final String heading, final String urlPrefix, final List<ClientRegistration> registrations) {
    final var links = new StringBuilder();
    for (final ClientRegistration registration : registrations) {
      final String href = urlPrefix + "/oauth2/authorization/" + registration.getRegistrationId();
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
            <html><head><meta charset="utf-8"><title>%s</title>
            <style>body{font-family:sans-serif;margin:2em;max-width:40em}
            li{margin:.5em 0}
            a{display:inline-block;padding:.5em 1em;background:#eef;
              color:#225;text-decoration:none;border-radius:4px}
            a:hover{background:#dde}</style></head><body>
            <h1>%s</h1>
            <p>Choose an identity provider:</p>
            <ul>%s</ul>
            </body></html>
            """)
        .formatted(escape(heading), escape(heading), links.toString());
  }

  private static String escape(final String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

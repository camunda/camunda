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
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Unprefixed multi-IdP picker at {@code /login}, served on CSL's standard {@code OidcWebapp} chain.
 * Lists every registration in the standard {@link ClientRegistrationRepository}. Spring Security's
 * {@code DefaultLoginPageGeneratingFilter} is NOT installed on CSL's OidcWebapp chain in our setup
 * — CSL configures a custom {@code authenticationEntryPoint} via {@code .exceptionHandling(eh ->
 * eh.authenticationEntryPoint(...))}, and Spring Security 7's {@code
 * DefaultLoginPageConfigurer.configure()} only adds the auto-picker filter when {@code
 * authenticationEntryPoint == null}. So the host has to render the unprefixed picker itself.
 *
 * <p>The PT-prefixed equivalent at {@code /physical-tenant/{tenantId}/login} is rendered by an
 * explicit {@code DefaultLoginPageGeneratingFilter} instance installed in {@code
 * PerTenantSecurityChainFactory#buildWebappChain} via {@code addFilterAfter(picker,
 * CsrfFilter.class)}.
 *
 * <p><b>PoC follow-up:</b> the whole picker dance (this controller + the explicit filter
 * installation on PT chains) is necessary because CSL doesn't ship a built-in picker. The clean
 * upstream fix would be either to let CSL's OidcWebapp chain pass through the auto-picker (drop the
 * custom entry point in favour of conditional logic that doesn't trip the configurer's gate) or to
 * expose a per-chain picker hook the host can configure.
 */
@Controller
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantLoginPageController {

  private final ClientRegistrationRepository clientRegistrationRepository;

  public PhysicalTenantLoginPageController(
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

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

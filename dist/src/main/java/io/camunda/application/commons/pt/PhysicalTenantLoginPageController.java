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
 * Unprefixed multi-IdP picker page at {@code /login}, served on CSL's standard {@code OidcWebapp}
 * chain. Lists every registration in the standard {@link ClientRegistrationRepository} (i.e. every
 * root-declared provider). Spring Security's {@link
 * org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter} is not
 * registered on CSL's OidcWebapp chain in our setup, so we render the picker ourselves here; {@code
 * /login} is permitAll'd on that chain by CSL.
 *
 * <p>The prefixed equivalent at {@code /physical-tenant/{tenantId}/login} is handled by Spring
 * Security's auto-generated picker — see {@code PerTenantSecurityChainFactory#buildWebappChain}
 * which reconfigures the filter's URL after build().
 *
 * <p><b>PoC follow-up:</b> this whole controller goes away once CSL's {@code OidcWebapp} chain
 * registers {@code DefaultLoginPageGeneratingFilter} itself — either by dropping any {@code
 * .loginPage(...)} override on its {@code oauth2Login(...)} (Spring then auto-registers the picker)
 * or by exposing a post-build hook the host can use to mutate the filter's URL the same way {@code
 * PerTenantSecurityChainFactory} does on the PT chains. For the real production implementation (PT
 * support migrated into CSL) this controller should not exist.
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

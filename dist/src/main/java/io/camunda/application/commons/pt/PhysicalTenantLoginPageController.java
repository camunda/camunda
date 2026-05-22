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
 * Host-rendered multi-IdP picker for the <b>unprefixed</b> {@code /login} URL only — the one served
 * by CSL's standard {@code OidcWebapp} chain. CSL configures a custom {@code
 * AuthenticationEntryPoint} on that chain which trips Spring Security 7's {@code
 * DefaultLoginPageConfigurer.configure()} gate (the auto-picker filter is only added when {@code
 * exceptionHandling.authenticationEntryPoint == null}), so no Spring-generated picker is installed
 * there and {@code /login} would otherwise 404. The picker lists every registration in the standard
 * {@link ClientRegistrationRepository} (i.e. every root-declared provider, no {@code
 * providers.assigned} filtering — accepted trade-off).
 *
 * <p>The PT-prefixed {@code /physical-tenant/{tenantId}/login} URLs do <b>not</b> route here — the
 * PT webapp chain installs its own {@link
 * org.springframework.security.web.authentication.ui.DefaultLoginPageGeneratingFilter} via {@code
 * PerTenantSecurityChainFactory#buildWebappChain} (configured with the PT's own {@code
 * ClientRegistrationRepository} so per-tenant assigned providers show up).
 *
 * <p>Tracked upstream as <a
 * href="https://github.com/camunda/camunda-security-library/issues/269">CSL#269</a> — once CSL
 * installs the picker filter itself on the {@code OidcWebapp} chain this controller can be deleted
 * entirely.
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
    return renderPicker(registrations);
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

  private static String renderPicker(final List<ClientRegistration> registrations) {
    final var links = new StringBuilder();
    for (final ClientRegistration registration : registrations) {
      final String href = "/oauth2/authorization/" + registration.getRegistrationId();
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
    return """
        <!doctype html>
        <html><head><meta charset="utf-8"><title>Sign in</title>
        <style>body{font-family:sans-serif;margin:2em;max-width:40em}
        li{margin:.5em 0}
        a{display:inline-block;padding:.5em 1em;background:#eef;
          color:#225;text-decoration:none;border-radius:4px}
        a:hover{background:#dde}</style></head><body>
        <h1>Sign in</h1>
        <p>Choose an identity provider:</p>
        <ul>%s</ul>
        </body></html>
        """
        .formatted(links.toString());
  }

  private static String escape(final String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

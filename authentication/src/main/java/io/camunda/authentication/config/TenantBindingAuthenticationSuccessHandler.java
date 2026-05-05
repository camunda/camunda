/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.filters.TenantBindingEnforcementFilter;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * Validates that the authenticated OIDC session was initiated through the tenant-aware picker (i.e.
 * the resolver wrote {@code BOUND_TENANT_ATTRIBUTE} on the session) and that the (tenantId, idpId)
 * pair is still valid against the {@link PhysicalTenantIdpRegistry} at the time of the callback
 * (defends against config changes mid-flow and the multi-tab race where two concurrent picker flows
 * overwrite the same session attribute).
 *
 * <p>Sessions that cannot be validated are invalidated and the response is 403. Otherwise the
 * default {@code SavedRequestAwareAuthenticationSuccessHandler} flow runs.
 *
 * <p>Backward compatibility: when the registry is empty (no tenant configuration) the handler
 * delegates to the default success behavior unchanged.
 */
public final class TenantBindingAuthenticationSuccessHandler
    extends SavedRequestAwareAuthenticationSuccessHandler {

  private final PhysicalTenantIdpRegistry registry;

  public TenantBindingAuthenticationSuccessHandler(final PhysicalTenantIdpRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void onAuthenticationSuccess(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Authentication authentication)
      throws IOException, ServletException {

    if (registry.tenantIds().isEmpty()) {
      // BC: no tenant configuration — feature is dormant.
      super.onAuthenticationSuccess(request, response, authentication);
      return;
    }

    if (!(authentication instanceof final OAuth2AuthenticationToken oauth)) {
      // Non-OIDC authentication is out of scope for tenant binding.
      super.onAuthenticationSuccess(request, response, authentication);
      return;
    }

    final var session = request.getSession(false);
    final var boundAttr =
        session == null
            ? null
            : session.getAttribute(TenantBindingEnforcementFilter.BOUND_TENANT_ATTRIBUTE);

    if (boundAttr == null) {
      rejectAndInvalidate(
          session, response, "OIDC session not bound to a tenant; re-login through the picker");
      return;
    }

    final var tenantId = boundAttr.toString();
    final var idpId = oauth.getAuthorizedClientRegistrationId();

    if (!registry.tenantIds().contains(tenantId)) {
      // Tenant was removed from registry mid-flow.
      rejectAndInvalidate(session, response, "Bound tenant is no longer configured");
      return;
    }

    if (!registry.getIdpsForTenant(tenantId).contains(idpId)) {
      // Either the assignment changed mid-flow, or the multi-tab race overwrote the session
      // attribute with a different tenant id while a different IdP's auth was completing.
      rejectAndInvalidate(session, response, "IdP is not assigned to the bound tenant");
      return;
    }

    super.onAuthenticationSuccess(request, response, authentication);
  }

  private static void rejectAndInvalidate(
      final HttpSession session, final HttpServletResponse response, final String reason)
      throws IOException {
    if (session != null) {
      try {
        session.invalidate();
      } catch (final IllegalStateException ignored) {
        // already invalidated — acceptable
      }
    }
    response.sendError(HttpServletResponse.SC_FORBIDDEN, reason);
  }
}

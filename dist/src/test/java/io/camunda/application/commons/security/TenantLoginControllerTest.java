/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.filters.PhysicalTenantAuthorizationFilter;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

public class TenantLoginControllerTest {

  // ───────── picker (GET /login/{tenantId}) ─────────

  @Test
  public void pickerReturns404ForUnknownTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default", List.of("default"))));

    final var response = controller.picker("unknown");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void pickerReturnsAllowedIdpsForTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(
                Map.of("risk-production", List.of("default", "provider-a"))));

    final var response = controller.picker("risk-production");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final var body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.tenantId()).isEqualTo("risk-production");
    assertThat(body.idps())
        .extracting(TenantLoginController.IdpOption::id)
        .containsExactly("default", "provider-a");
    assertThat(body.idps())
        .extracting(TenantLoginController.IdpOption::loginUrl)
        .containsExactly("/login/risk-production/default", "/login/risk-production/provider-a");
  }

  @Test
  public void pickerReturnsEmptyListWhenTenantHasNoIdps() {
    final var controller =
        new TenantLoginController(new PhysicalTenantIdpRegistry(Map.of("orphan", List.of())));

    final var response = controller.picker("orphan");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().idps()).isEmpty();
  }

  // ───────── login start (GET /login/{tenantId}/{idpId}) ─────────

  @Test
  public void loginStartReturns404ForUnknownTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default", List.of("default"))));
    final var req = new MockHttpServletRequest();

    final var response = controller.startLogin("unknown", "default", req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(req.getSession(false)).isNull();
  }

  @Test
  public void loginStartReturns400WhenIdpNotAssignedToTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(Map.of("default", List.of("default"))));
    final var req = new MockHttpServletRequest();

    final var response = controller.startLogin("default", "provider-a", req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(req.getSession(false)).isNull();
  }

  @Test
  public void loginStartStampsSessionAndRedirectsToOauth2Authorization() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(
                Map.of("risk-production", List.of("default", "provider-a"))));
    final var req = new MockHttpServletRequest();

    final var response = controller.startLogin("risk-production", "provider-a", req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation()).hasToString("/oauth2/authorization/provider-a");
    assertThat(
            req.getSession().getAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE))
        .isEqualTo("risk-production");
  }

  @Test
  public void loginStartReturns409WhenSessionAlreadyBoundToDifferentTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(
                Map.of(
                    "default", List.of("default"),
                    "risk-production", List.of("default"))));
    final var req = new MockHttpServletRequest();
    req.getSession()
        .setAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE, "default");

    final var response = controller.startLogin("risk-production", "default", req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            req.getSession().getAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE))
        .isEqualTo("default");
  }

  @Test
  public void loginStartIsIdempotentWhenSessionBoundToSameTenant() {
    final var controller =
        new TenantLoginController(
            new PhysicalTenantIdpRegistry(
                Map.of("risk-production", List.of("default", "provider-a"))));
    final var req = new MockHttpServletRequest();
    req.getSession()
        .setAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE, "risk-production");

    final var response = controller.startLogin("risk-production", "default", req);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation()).hasToString("/oauth2/authorization/default");
    assertThat(
            req.getSession().getAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE))
        .isEqualTo("risk-production");
  }
}

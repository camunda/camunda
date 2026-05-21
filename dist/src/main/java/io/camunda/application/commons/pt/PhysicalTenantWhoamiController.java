/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * PoC API endpoint exercising the per-tenant API SecurityFilterChain. Declares its path relative to
 * {@code /v2} only — the existing {@code PhysicalTenantRequestMappingHandlerMapping} in {@code
 * zeebe/gateway-rest} auto-registers the two PT-prefixed siblings (see spec D7):
 *
 * <ul>
 *   <li>{@code /v2/physical-tenants/{physicalTenantId}/whoami} — direct API-client URL.
 *   <li>{@code /physical-tenant/{physicalTenantId}/v2/whoami} — webapp/SPA URL.
 * </ul>
 *
 * <p>The bare {@code /v2/whoami} path is registered as a side effect; the PoC's pt-security chains
 * don't match it, so it falls through to whatever the host serves under {@code /v2}.
 */
@CamundaRestController
@Profile("pt-security")
public class PhysicalTenantWhoamiController {

  public record Whoami(String tenantId, String principal) {}

  @GetMapping("/v2/whoami")
  public Whoami whoamiApi(
      @PathVariable(name = "physicalTenantId", required = false) final String physicalTenantId,
      final Authentication authentication) {
    final String resolved =
        physicalTenantId != null ? physicalTenantId : PhysicalTenantContext.current();
    return new Whoami(resolved, authentication != null ? authentication.getName() : "anonymous");
  }
}

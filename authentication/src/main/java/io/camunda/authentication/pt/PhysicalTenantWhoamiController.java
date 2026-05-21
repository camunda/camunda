/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile("pt-security")
public class PhysicalTenantWhoamiController {

  public record Whoami(String tenantId, String principal) {}

  @GetMapping("/physical-tenant/{tenantId}/whoami")
  @ResponseBody
  public Whoami whoami(@PathVariable final String tenantId, final Authentication authentication) {
    return new Whoami(tenantId, authentication != null ? authentication.getName() : "anonymous");
  }

  @GetMapping("/v2/physical-tenants/{tenantId}/whoami")
  @ResponseBody
  public Whoami whoamiApi(
      @PathVariable final String tenantId, final Authentication authentication) {
    return new Whoami(tenantId, authentication != null ? authentication.getName() : "anonymous");
  }
}

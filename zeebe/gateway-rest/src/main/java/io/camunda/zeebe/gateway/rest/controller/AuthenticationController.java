/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResult;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResultTenantsInner;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@Profile("consolidated-auth")
@CamundaRestController
@RequestMapping("/v2/authentication")
public class AuthenticationController {

  private final CamundaAuthenticationProvider authenticationProvider;

  public AuthenticationController(final CamundaAuthenticationProvider authenticationProvider) {
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaGetMapping(path = "/me")
  public ResponseEntity<CamundaUserResult> getCurrentUser() {
    final var authentication = authenticationProvider.getCamundaAuthentication();

    return authentication == null
            || authentication.equals(CamundaAuthentication.NONE_AUTHENTICATION)
        ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        : ResponseEntity.ok(toCamundaUserResult(authentication));
  }

  protected CamundaUserResult toCamundaUserResult(final CamundaAuthentication authentication) {
    final var result = new CamundaUserResult();
    result.userId(authentication.getUsername());
    result.displayName(
        Optional.ofNullable(authentication.getDisplayName()).orElse(authentication.getEmail()));
    result.c8Links(List.of());
    result.canLogout(true);
    result.authorizedApplications(List.of("*"));
    result.setGroups(authentication.getGroupIds());
    result.setRoles(authentication.getRoleIds());
    final List<CamundaUserResultTenantsInner> tenants = new ArrayList<>();
    authentication
        .getTenantIds()
        .forEach(tenantId -> tenants.add(new CamundaUserResultTenantsInner().tenantId(tenantId)));
    result.setTenants(tenants);

    return result;
  }
}

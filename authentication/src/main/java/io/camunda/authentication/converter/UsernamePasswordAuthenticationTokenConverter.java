/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.authentication.service.BasicMembershipService;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class UsernamePasswordAuthenticationTokenConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final BasicMembershipService membershipService;

  public UsernamePasswordAuthenticationTokenConverter(
      final BasicMembershipService membershipService) {
    this.membershipService = membershipService;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof UsernamePasswordAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var username = authentication.getName();
    final var resolver = membershipService.newResolver(username);
    // BASIC auth has no token claims to match mapping rules against, so we deliberately skip
    // wiring mappingRulesSupplier — authenticatedMappingRuleIds() will simply return List.of().
    return CamundaAuthentication.of(
        a ->
            a.user(username)
                .groupIdsSupplier(resolver::groups)
                .roleIdsSupplier(resolver::roles)
                .tenantsSupplier(resolver::tenants));
  }
}

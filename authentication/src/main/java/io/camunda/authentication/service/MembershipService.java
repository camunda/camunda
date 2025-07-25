/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.search.entities.RoleEntity;
import io.camunda.service.TenantServices.TenantDTO;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public abstract class MembershipService {
  public abstract MembershipResult resolveMemberships(
      Map<String, Object> claims, String username, String clientId)
      throws OAuth2AuthenticationException;

  public record MembershipResult(
      Set<String> groups,
      List<RoleEntity> roles,
      List<TenantDTO> tenants,
      Set<String> mappings,
      List<String> authorizedApplications) {}
}

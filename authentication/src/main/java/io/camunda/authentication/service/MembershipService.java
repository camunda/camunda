/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public interface MembershipService {
  MembershipResult resolveMemberships(Map<String, Object> claims, String username, String clientId)
      throws OAuth2AuthenticationException;

  record MembershipResult(
      Set<String> groups, Set<String> roles, List<String> tenants, Set<String> mappings) {}
}

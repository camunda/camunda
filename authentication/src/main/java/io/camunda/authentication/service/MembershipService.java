/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import io.camunda.authentication.entity.OAuthContext;
import io.camunda.authentication.service.PrincipalExtractionHelper.PrincipalExtractionResult;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

public interface MembershipService {
  OAuthContext resolveMemberships(
      Map<String, Object> claims, PrincipalExtractionResult principalResult)
      throws OAuth2AuthenticationException;
}

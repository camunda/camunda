/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.converter;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.PrincipalType;
import io.camunda.auth.domain.spi.MembershipResolver;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenClaimsConverter {

  private static final Logger LOG = LoggerFactory.getLogger(TokenClaimsConverter.class);

  private final String usernameClaim;
  private final String clientIdClaim;
  private final boolean preferUsernameClaim;
  private final MembershipResolver membershipResolver;

  public TokenClaimsConverter(
      final String usernameClaim,
      final String clientIdClaim,
      final boolean preferUsernameClaim,
      final MembershipResolver membershipResolver) {
    this.usernameClaim = Objects.requireNonNull(usernameClaim);
    this.clientIdClaim = clientIdClaim;
    this.preferUsernameClaim = preferUsernameClaim;
    this.membershipResolver = Objects.requireNonNull(membershipResolver);
  }

  public CamundaAuthentication convert(final Map<String, Object> claims) {
    final String username = extractClaim(claims, usernameClaim);
    final String clientId = clientIdClaim != null ? extractClaim(claims, clientIdClaim) : null;

    final String principalId;
    final PrincipalType principalType;

    if (preferUsernameClaim && username != null) {
      principalId = username;
      principalType = PrincipalType.USER;
    } else if (clientId != null && username == null) {
      principalId = clientId;
      principalType = PrincipalType.CLIENT;
    } else if (username != null) {
      principalId = username;
      principalType = PrincipalType.USER;
    } else {
      LOG.warn("No username or clientId found in token claims");
      return CamundaAuthentication.anonymous();
    }

    return membershipResolver.resolveMemberships(claims, principalId, principalType);
  }

  private String extractClaim(final Map<String, Object> claims, final String claimName) {
    final Object value = claims.get(claimName);
    return value != null ? value.toString() : null;
  }
}

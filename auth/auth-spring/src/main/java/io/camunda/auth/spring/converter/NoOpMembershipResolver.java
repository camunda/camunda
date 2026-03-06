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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpMembershipResolver implements MembershipResolver {

  private static final Logger LOG = LoggerFactory.getLogger(NoOpMembershipResolver.class);

  private final String groupsClaim;

  public NoOpMembershipResolver() {
    this(null);
  }

  public NoOpMembershipResolver(final String groupsClaim) {
    this.groupsClaim = groupsClaim;
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> claims,
      final String principalId,
      final PrincipalType principalType) {

    final List<String> groups =
        groupsClaim != null ? extractGroups(claims, groupsClaim) : List.of();

    final var builder = new CamundaAuthentication.Builder();
    if (principalType == PrincipalType.USER) {
      builder.user(principalId);
    } else {
      builder.clientId(principalId);
    }
    return builder.groupIds(groups).claims(claims).build();
  }

  @SuppressWarnings("unchecked")
  private List<String> extractGroups(
      final Map<String, Object> claims, final String claimName) {
    final Object value = claims.get(claimName);
    if (value == null) {
      return List.of();
    }
    if (value instanceof Collection<?> collection) {
      final List<String> result = new ArrayList<>();
      for (final Object item : collection) {
        result.add(item.toString());
      }
      return result;
    }
    if (value instanceof String str) {
      return List.of(str);
    }
    LOG.warn("Unexpected type for groups claim '{}': {}", claimName, value.getClass());
    return List.of();
  }
}

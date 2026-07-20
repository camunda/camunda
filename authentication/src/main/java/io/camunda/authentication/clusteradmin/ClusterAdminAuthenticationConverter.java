/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Converts a cluster-admin {@link Authentication} into a membership-free {@link
 * CamundaAuthentication} carrying only the principal name — a client id for OIDC bearer principals
 * ({@link JwtAuthenticationToken}, client_credentials has no username) or a username for Basic.
 *
 * <p>Registered ahead of CSL's DB-backed converter so cluster-admin principals do not pick up DB
 * memberships on name collision. It only claims authentications marked with {@link
 * ClusterAdminSecurityConfiguration#CLUSTER_ADMIN_AUTHORITY}.
 */
public final class ClusterAdminAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getAuthorities().stream()
            .anyMatch(
                authority ->
                    ClusterAdminSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY.equals(
                        authority.getAuthority()));
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    // Build the principal from its name alone — no group/role/tenant lookup (that isolation is the
    // whole point of this converter). The builder takes either a user or a client id, not both:
    // a bearer token is an OIDC client (client_credentials, no username), so it becomes a client
    // id; a Basic login is a user.
    final String name = authentication.getName();
    return authentication instanceof JwtAuthenticationToken
        ? CamundaAuthentication.of(builder -> builder.clientId(name))
        : CamundaAuthentication.of(builder -> builder.user(name));
  }
}

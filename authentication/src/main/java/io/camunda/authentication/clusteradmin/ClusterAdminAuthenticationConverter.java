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

/**
 * Converts a cluster-admin {@link Authentication} into a {@link CamundaAuthentication} with only
 * the configured username.
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
    // Username only; groups/roles/tenants deliberately left empty (no MembershipPort lookup).
    return CamundaAuthentication.of(builder -> builder.user(authentication.getName()));
  }
}

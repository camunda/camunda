/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;

/**
 * Whether mapping rules can be assigned to and unassigned from roles and groups. On SaaS, mapping
 * rules can only be managed once an additional IdP is configured; without one, an administrator
 * could otherwise remove the default mapping rules, such as the one that gives users the admin
 * role. The Admin UI applies the same condition to its mapping rules screens.
 */
public final class MappingRuleMembershipPolicy {

  static final String DETAIL =
      "Mapping rules can't be assigned to or unassigned from roles and groups on a SaaS cluster"
          + " without an additional identity provider.";

  private final boolean manageable;

  public MappingRuleMembershipPolicy(final boolean manageable) {
    this.manageable = manageable;
  }

  /**
   * Manageable outside SaaS, and on SaaS when a named {@code providers.oidc.*} entry, the
   * additional IdP, sets a client ID.
   */
  public static MappingRuleMembershipPolicy of(final CamundaSecurityLibraryProperties properties) {
    final var saas = StringUtils.hasText(properties.getSaas().getOrganizationId());
    return new MappingRuleMembershipPolicy(!saas || isAdditionalIdpConfigured(properties));
  }

  /** A {@code 403} problem when mapping rules can't be managed, else nothing. */
  Either<ProblemDetail, Void> requireManageable() {
    if (manageable) {
      return Either.right(null);
    }
    final var problemDetail = CamundaProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, DETAIL);
    problemDetail.setTitle("FORBIDDEN");
    return Either.left(problemDetail);
  }

  private static boolean isAdditionalIdpConfigured(
      final CamundaSecurityLibraryProperties properties) {
    final var providers = properties.getAuthentication().getProviders();
    final Map<String, OidcConfiguration> named = providers != null ? providers.getOidc() : null;
    return named != null
        && named.values().stream()
            .filter(Objects::nonNull)
            .anyMatch(oidc -> StringUtils.hasText(oidc.getClientId()));
  }
}

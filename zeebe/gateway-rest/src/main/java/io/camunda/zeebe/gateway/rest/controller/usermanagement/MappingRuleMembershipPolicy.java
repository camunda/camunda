/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.util.Either;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.util.StringUtils;

/**
 * Whether mapping rules can be assigned to and unassigned from roles and groups. Mapping rules
 * aren't supported on SaaS; there, an administrator could otherwise remove the default mapping
 * rules, such as the one that gives users the admin role. The Admin UI hides its mapping rules
 * screens on SaaS as well.
 */
public final class MappingRuleMembershipPolicy {

  static final String DETAIL =
      "Mapping rules can't be assigned to or unassigned from roles and groups on a SaaS cluster.";

  private final boolean manageable;

  public MappingRuleMembershipPolicy(final boolean manageable) {
    this.manageable = manageable;
  }

  /** Manageable outside SaaS only. */
  public static MappingRuleMembershipPolicy of(final SecurityConfiguration configuration) {
    return new MappingRuleMembershipPolicy(
        !StringUtils.hasText(configuration.getSaas().getOrganizationId()));
  }

  /** A {@code 403} problem when mapping rules can't be managed, else nothing. */
  Either<ProblemDetail, Void> requireManageable() {
    if (manageable) {
      return Either.right(null);
    }
    return Either.left(
        RestErrorMapper.createProblemDetail(HttpStatus.FORBIDDEN, DETAIL, "FORBIDDEN"));
  }
}

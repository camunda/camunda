/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.createProblemDetail;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import io.micrometer.common.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class MultiTenancyValidator {
  private static final String MESSAGE_FORMAT =
      "Expected to handle request %s with tenant identifier '%s', but %s";
  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  public static Optional<ProblemDetail> validateAuthorization(
      final String tenantId, final boolean multiTenancyEnabled, final String commandName) {
    if (!multiTenancyEnabled) {
      return Optional.empty();
    }

    final var authorizedTenants = RequestMapper.getAuthentication().authenticatedTenantIds();
    if (authorizedTenants == null || !authorizedTenants.contains(tenantId)) {
      return Optional.of(
          RestErrorMapper.createProblemDetail(
              HttpStatus.UNAUTHORIZED,
              MESSAGE_FORMAT.formatted(
                  commandName, tenantId, "tenant is not authorized to perform this request"),
              HttpStatus.UNAUTHORIZED.name()));
    }

    return Optional.empty();
  }

  public static Either<ProblemDetail, String> validateTenantId(
      final String tenantId, final boolean multiTenancyEnabled, final String commandName) {
    final var hasTenantId = !StringUtils.isBlank(tenantId);
    if (!multiTenancyEnabled) {
      if (hasTenantId && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)) {
        return Either.left(
            RestErrorMapper.createProblemDetail(
                HttpStatus.BAD_REQUEST,
                MESSAGE_FORMAT.formatted(commandName, tenantId, "multi-tenancy is disabled"),
                INVALID_ARGUMENT.name()));
      }

      return Either.right(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    final List<String> violations = new ArrayList<>();
    if (!hasTenantId) {
      violations.add(
          MESSAGE_FORMAT.formatted(commandName, tenantId, "no tenant identifier was provided"));
    } else if (tenantId.length() > 31) {
      violations.add(
          MESSAGE_FORMAT.formatted(
              commandName, tenantId, "tenant identifier is longer than 31 characters"));
    } else if (!TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
        && !TENANT_ID_MASK.matcher(tenantId).matches()) {
      violations.add(
          MESSAGE_FORMAT.formatted(
              commandName, tenantId, "tenant identifier contains illegal characters"));
    }

    final var problemDetail = createProblemDetail(violations);
    return problemDetail
        .<Either<ProblemDetail, String>>map(Either::left)
        .orElseGet(() -> Either.right(tenantId));
  }
}

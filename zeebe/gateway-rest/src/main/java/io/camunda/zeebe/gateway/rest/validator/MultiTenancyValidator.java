/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TENANT;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.createProblemDetail;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class MultiTenancyValidator {
  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

  /**
   * Validates whether a tenant is authorized to perform the request. It does so by checking the
   * provided tenant against the list of authorized tenant in the authentication context.
   *
   * @param tenantId the tenant to check if it's authorized
   * @param multiTenancyEnabled whether multi-tenancy is enabled
   * @param commandName the name of the command, used for error messages
   * @return a optional {@link ProblemDetail} if the tenant is not authorized
   */
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
              ERROR_MESSAGE_INVALID_TENANT.formatted(
                  commandName, tenantId, "tenant is not authorized to perform this request"),
              HttpStatus.UNAUTHORIZED.name()));
    }

    return Optional.empty();
  }

  /**
   * Validates the tenantId. If multi-tenancy is disabled, the tenantId must be empty, or the
   * default tenant. if multi-tenancy is enabled a tenantId must be provided and should match the
   * tenantId mask.
   *
   * <p>If all validations succeed the method returns the tenantId that should be used in the
   * request. This must always be set in the request, as there is no guarantee that the client
   * provided a tenantId.
   *
   * @param tenantId the tenantId to validate
   * @param multiTenancyEnabled whether multi-tenancy is enabled
   * @param commandName the name of the command, used for error messages
   * @return a {@link Either} containing a {@link ProblemDetail} if the tenantId is invalid, or the
   *     tenantId if it's valid
   */
  public static Either<ProblemDetail, String> validateTenantId(
      final String tenantId, final boolean multiTenancyEnabled, final String commandName) {
    final var hasTenantId = tenantId != null && !tenantId.isBlank();
    if (!multiTenancyEnabled) {
      if (hasTenantId && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)) {
        return Either.left(
            RestErrorMapper.createProblemDetail(
                HttpStatus.BAD_REQUEST,
                ERROR_MESSAGE_INVALID_TENANT.formatted(
                    commandName, tenantId, "multi-tenancy is disabled"),
                INVALID_ARGUMENT.name()));
      }

      return Either.right(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    final List<String> violations = new ArrayList<>();
    if (!hasTenantId) {
      violations.add(
          ERROR_MESSAGE_INVALID_TENANT.formatted(
              commandName, tenantId, "no tenant identifier was provided"));
    } else if (tenantId.length() > 31) {
      violations.add(
          ERROR_MESSAGE_INVALID_TENANT.formatted(
              commandName, tenantId, "tenant identifier is longer than 31 characters"));
    } else if (!TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
        && !TENANT_ID_MASK.matcher(tenantId).matches()) {
      violations.add(
          ERROR_MESSAGE_INVALID_TENANT.formatted(
              commandName, tenantId, "tenant identifier contains illegal characters"));
    }

    final var problemDetail = createProblemDetail(violations);
    return problemDetail
        .<Either<ProblemDetail, String>>map(Either::left)
        .orElseGet(() -> Either.right(tenantId));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TENANT;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_TENANTS;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.createProblemDetail;
import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class MultiTenancyValidator {
  private static final Pattern TENANT_ID_MASK = Pattern.compile("^[\\w\\.-]{1,31}$");

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
    return validateTenantIds(
            tenantId == null ? List.of() : List.of(tenantId), multiTenancyEnabled, commandName)
        .map(List::getFirst);
  }

  public static Either<ProblemDetail, List<String>> validateTenantIds(
      final List<String> tenantIds, final boolean multiTenancyEnabled, final String commandName) {
    final var hasTenantId =
        tenantIds != null
            && !tenantIds.isEmpty()
            && tenantIds.stream().anyMatch(id -> !id.isBlank());
    if (!multiTenancyEnabled) {
      if (hasTenantId
          && tenantIds.stream()
              .anyMatch(tenantId -> !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId))) {
        // Anything else than the default tenant was provided
        return Either.left(
            RestErrorMapper.createProblemDetail(
                HttpStatus.BAD_REQUEST,
                tenantIds.size() > 1
                    ? ERROR_MESSAGE_INVALID_TENANTS.formatted(
                        commandName, tenantIds, "multi-tenancy is disabled")
                    : ERROR_MESSAGE_INVALID_TENANT.formatted(
                        commandName, tenantIds.getFirst(), "multi-tenancy is disabled"),
                INVALID_ARGUMENT.name()));
      }

      return Either.right(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    }

    final List<String> violations = new ArrayList<>();
    if (!hasTenantId) {
      violations.add(
          ERROR_MESSAGE_INVALID_TENANTS.formatted(
              commandName, tenantIds, "no tenant identifier was provided"));
    } else {
      tenantIds.forEach(
          tenantId -> {
            if (tenantId.length() > 31) {
              violations.add(
                  ERROR_MESSAGE_INVALID_TENANT.formatted(
                      commandName, tenantId, "tenant identifier is longer than 31 characters"));
            } else if (!TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
                && !TENANT_ID_MASK.matcher(tenantId).matches()) {
              violations.add(
                  ERROR_MESSAGE_INVALID_TENANT.formatted(
                      commandName, tenantId, "tenant identifier contains illegal characters"));
            }
          });
    }

    final var problemDetail = createProblemDetail(violations);
    return problemDetail
        .<Either<ProblemDetail, List<String>>>map(Either::left)
        .orElseGet(() -> Either.right(tenantIds));
  }
}

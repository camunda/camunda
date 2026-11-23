/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.AdvancedSecretStatusFilter;
import io.camunda.gateway.protocol.model.SecretListRequest;
import io.camunda.gateway.protocol.model.SecretResolveRequest;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

public final class SecretRequestValidator {

  /**
   * Bounds the number of per-reference authorization checks a single resolve request can trigger.
   *
   * <p>This is the sole source of truth for the cap in code. The {@code maxItems: 20} on {@code
   * SecretResolveRequest.references} in {@code secrets.yaml} documents the same limit in the API
   * contract, but it is <b>not enforced by the generated request model</b>: the model tree used by
   * the controller does not emit array-size ({@code @Size}) constraints, so the cap must be checked
   * here. Keep the two values in sync — {@code SecretRequestValidatorSpecSyncTest} guards against
   * drift between them.
   */
  public static final int MAX_BATCH_SIZE = 20;

  private SecretRequestValidator() {}

  public static Optional<ProblemDetail> validateSecretResolveRequest(
      final SecretResolveRequest request) {
    return validate(
        violations -> {
          final var references = request == null ? null : request.getReferences();
          if (references == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("references"));
            return;
          }
          if (references.size() > MAX_BATCH_SIZE) {
            violations.add(
                "At most %d references may be requested in a single batch, but %d were provided."
                    .formatted(MAX_BATCH_SIZE, references.size()));
          }
          if (references.stream().anyMatch(Objects::isNull)) {
            violations.add("The references list must not contain null entries.");
          }
        });
  }

  public static Optional<ProblemDetail> validateSecretListRequest(
      final @Nullable SecretListRequest request) {
    // The body, its filter and the filter's status are all optional, and an unknown status never
    // gets here, since deserializing the request already fails on it and
    // GlobalControllerExceptionHandler answers 400 with the allowed values. A null list entry
    // does deserialize, so it is rejected here.
    return validate(
        violations -> {
          final var filter = request == null ? null : request.getFilter();
          if (filter != null
              && filter.getStatus() instanceof final AdvancedSecretStatusFilter status) {
            rejectNullEntries(status.get$In(), "filter.status.$in", violations);
            rejectNullEntries(status.get$NotIn(), "filter.status.$notIn", violations);
          }
        });
  }

  private static void rejectNullEntries(
      final @Nullable List<?> values, final String field, final List<String> violations) {
    if (values != null && values.stream().anyMatch(Objects::isNull)) {
      violations.add("The %s list must not contain null entries.".formatted(field));
    }
  }
}

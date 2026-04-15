/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_ALL_REQUIRED_FIELD;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;

import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceMigrationInstructionContract;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.ProblemDetail;

public class ProcessInstanceRequestValidator {

  /**
   * Validate only the tags for strict contract paths (other fields validated by spec constraints).
   */
  public static Optional<ProblemDetail> validateCreateProcessInstanceTags(final Set<String> tags) {
    return validate(violations -> validateTags(tags, violations));
  }

  public static Optional<ProblemDetail> validateMigrationInstructions(
      final ProcessInstanceMigrationInstructionContract request) {
    return validate(
        violations -> {
          if (request.mappingInstructions() == null || request.mappingInstructions().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
          } else {
            final boolean allValid =
                request.mappingInstructions().stream()
                    .allMatch(
                        mi ->
                            (mi.sourceElementId() != null && !mi.sourceElementId().isEmpty())
                                && (mi.targetElementId() != null
                                    && !mi.targetElementId().isEmpty()));
            if (!allValid) {
              violations.add(
                  ERROR_MESSAGE_ALL_REQUIRED_FIELD.formatted(
                      List.of("sourceElementId", "targetElementId")));
            }
          }
        });
  }

  private static void validateTags(final Set<String> tags, final List<String> violations) {
    violations.addAll(TagsValidator.validate(tags));
  }
}

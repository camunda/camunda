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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public final class AdHocSubProcessRequestValidator {

  public static Optional<ProblemDetail> validateActivateActivitiesRequest(
      final GeneratedAdHocSubProcessActivateActivitiesInstructionStrictContract request) {
    return validate(
        violations -> {
          if (request.elements() == null || request.elements().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elements"));
          } else {
            for (int i = 0; i < request.elements().size(); i++) {
              final var elementId = request.elements().get(i).elementId();
              if (elementId == null || elementId.isBlank()) {
                violations.add(
                    ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elements[%d].elementId".formatted(i)));
              }
            }
          }
        });
  }
}

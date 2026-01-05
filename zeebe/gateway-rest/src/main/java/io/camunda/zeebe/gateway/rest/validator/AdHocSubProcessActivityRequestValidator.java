/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.gateway.protocol.model.AdHocSubProcessActivateActivitiesInstruction;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;

public class AdHocSubProcessActivityRequestValidator {
  public static Optional<ProblemDetail> validateAdHocSubProcessActivationRequest(
      final AdHocSubProcessActivateActivitiesInstruction request) {
    return validate(
        violations -> {
          if (request.getElements() == null || request.getElements().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elements"));
          } else {
            for (int i = 0; i < request.getElements().size(); i++) {
              if (StringUtils.isBlank(request.getElements().get(i).getElementId())) {
                violations.add(
                    ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elements[%d].elementId".formatted(i)));
              }
            }
          }
        });
  }
}

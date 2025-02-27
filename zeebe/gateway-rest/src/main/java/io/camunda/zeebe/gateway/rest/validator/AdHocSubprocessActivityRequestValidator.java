/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.AdHocSubprocessActivityFilter;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ProblemDetail;

public class AdHocSubprocessActivityRequestValidator {

  public static Optional<ProblemDetail> validateAdHocSubprocessSearchActivitiesRequest(
      final AdHocSubprocessActivityFilter filter, final Long processDefinitionKey) {
    return validate(
        violations -> {
          if (processDefinitionKey == null || processDefinitionKey <= 0) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "filter.processDefinitionKey",
                    processDefinitionKey,
                    "a non-negative numeric value"));
          }

          if (StringUtils.isBlank(filter.getAdHocSubprocessId())) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter.adHocSubprocessId"));
          }
        });
  }
}

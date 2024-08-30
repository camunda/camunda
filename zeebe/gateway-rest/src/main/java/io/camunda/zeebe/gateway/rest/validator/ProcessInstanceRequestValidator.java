/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import io.camunda.zeebe.gateway.protocol.rest.StartProcessInstanceRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ProcessInstanceRequestValidator {

  public static Optional<ProblemDetail> validateStartProcessInstanceRequest(
      final StartProcessInstanceRequest request) {
    return validate(
        violations -> {
          if (request.getBpmnProcessId() == null && request.getProcessDefinitionKey() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("bpmnProcessId", "processDefinitionKey")));
          }
          if (request.getBpmnProcessId() != null && request.getProcessDefinitionKey() != null) {
            violations.add(
                ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                    List.of("bpmnProcessId", "processDefinitionKey")));
          }
          if (request.getOperationReference() != null && request.getOperationReference() < 0) {
            violations.add(
                ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(
                    "operationalReference", request.getOperationReference(), "greater than 0"));
          }
        });
  }
}

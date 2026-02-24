/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRdbmsDisabled
public class ProcessInstanceRequestValidator {
  private final CreateRequestOperationValidator createRequestOperationValidator;

  public ProcessInstanceRequestValidator(
      @NotNull final CreateRequestOperationValidator createRequestOperationValidator) {
    this.createRequestOperationValidator = createRequestOperationValidator;
  }

  public void validateCreateOperationRequest(
      final CreateOperationRequestDto operationRequest, final String processInstanceId) {
    createRequestOperationValidator.validate(operationRequest, processInstanceId);
  }
}

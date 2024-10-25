/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class CreateBatchOperationRequestValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CreateBatchOperationRequestValidator.class);

  public void validate(final CreateBatchOperationRequestDto batchOperationRequest) {
    if (batchOperationRequest.getQuery() == null) {
      throw new InvalidRequestException("List view query must be defined.");
    }

    final OperationType operationType = batchOperationRequest.getOperationType();
    if (operationType == null) {
      throw new InvalidRequestException("Operation type must be defined.");
    }

    if (operationType != OperationType.MODIFY_PROCESS_INSTANCE
        && batchOperationRequest.getModifications() != null) {
      throw new InvalidRequestException(
          String.format("Modifications field not supported for %s operation", operationType));
    }

    switch (operationType) {
      case UPDATE_VARIABLE:
      case ADD_VARIABLE:
        throw new InvalidRequestException(
            "For variable update use \"Create operation for one process instance\" endpoint.");

      case MIGRATE_PROCESS_INSTANCE:
        validateMigrateProcessInstanceType(batchOperationRequest);
        break;

      case MODIFY_PROCESS_INSTANCE:
        validateModifyProcessInstanceType(batchOperationRequest);
        break;
      default:
        break;
    }
  }

  private void validateModifyProcessInstanceType(
      final CreateBatchOperationRequestDto batchOperationRequest) {
    final List<Modification> modifications = batchOperationRequest.getModifications();
    if (CollectionUtils.isEmpty(modifications)) {
      throw new InvalidRequestException("Operation requires a single modification entry.");
    } else if (modifications.size() > 1) {
      LOGGER.warn("Multiple modifications in request, only one will be processed.");
      batchOperationRequest.setModifications(List.of(modifications.get(0)));
    }
  }

  private void validateMigrateProcessInstanceType(
      final CreateBatchOperationRequestDto batchOperationRequest) {
    final MigrationPlanDto migrationPlanDto = batchOperationRequest.getMigrationPlan();
    if (migrationPlanDto == null) {
      throw new InvalidRequestException(
          String.format(
              "Migration plan is mandatory for %s operation",
              OperationType.MIGRATE_PROCESS_INSTANCE));
    }
    migrationPlanDto.validate();
  }
}

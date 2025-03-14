/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static io.camunda.search.entities.IncidentEntity.ErrorType.CALLED_DECISION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.CALLED_ELEMENT_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.CONDITION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.DECISION_EVALUATION_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.EXECUTION_LISTENER_NO_RETRIES;
import static io.camunda.search.entities.IncidentEntity.ErrorType.EXTRACT_VALUE_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.FORM_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.IO_MAPPING_ERROR;
import static io.camunda.search.entities.IncidentEntity.ErrorType.JOB_NO_RETRIES;
import static io.camunda.search.entities.IncidentEntity.ErrorType.MESSAGE_SIZE_EXCEEDED;
import static io.camunda.search.entities.IncidentEntity.ErrorType.RESOURCE_NOT_FOUND;
import static io.camunda.search.entities.IncidentEntity.ErrorType.TASK_LISTENER_NO_RETRIES;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNHANDLED_ERROR_EVENT;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNKNOWN;
import static io.camunda.search.entities.IncidentEntity.ErrorType.UNSPECIFIED;
import static io.camunda.search.entities.IncidentEntity.IncidentState.ACTIVE;
import static io.camunda.search.entities.IncidentEntity.IncidentState.MIGRATED;
import static io.camunda.search.entities.IncidentEntity.IncidentState.PENDING;
import static io.camunda.search.entities.IncidentEntity.IncidentState.RESOLVED;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;

public class IncidentEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.operate.IncidentEntity, IncidentEntity> {

  @Override
  public IncidentEntity apply(
      final io.camunda.webapps.schema.entities.operate.IncidentEntity value) {
    return new IncidentEntity(
        value.getKey(),
        value.getProcessDefinitionKey(),
        value.getBpmnProcessId(),
        value.getProcessInstanceKey(),
        toErrorType(value.getErrorType()),
        value.getErrorMessage(),
        value.getFlowNodeId(),
        value.getFlowNodeInstanceKey(),
        value.getCreationTime(),
        toState(value.getState()),
        value.getJobKey(),
        value.getTenantId());
  }

  private IncidentState toState(
      final io.camunda.webapps.schema.entities.operate.IncidentState value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case ACTIVE -> ACTIVE;
      case MIGRATED -> MIGRATED;
      case RESOLVED -> RESOLVED;
      case PENDING -> PENDING;
      default -> throw new IllegalArgumentException("Unexpected value: " + value);
    };
  }

  private ErrorType toErrorType(final io.camunda.webapps.schema.entities.operate.ErrorType value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case UNSPECIFIED -> UNSPECIFIED;
      case UNKNOWN -> UNKNOWN;
      case IO_MAPPING_ERROR -> IO_MAPPING_ERROR;
      case JOB_NO_RETRIES -> JOB_NO_RETRIES;
      case EXECUTION_LISTENER_NO_RETRIES -> EXECUTION_LISTENER_NO_RETRIES;
      case TASK_LISTENER_NO_RETRIES -> TASK_LISTENER_NO_RETRIES;
      case CONDITION_ERROR -> CONDITION_ERROR;
      case EXTRACT_VALUE_ERROR -> EXTRACT_VALUE_ERROR;
      case CALLED_ELEMENT_ERROR -> CALLED_ELEMENT_ERROR;
      case UNHANDLED_ERROR_EVENT -> UNHANDLED_ERROR_EVENT;
      case MESSAGE_SIZE_EXCEEDED -> MESSAGE_SIZE_EXCEEDED;
      case CALLED_DECISION_ERROR -> CALLED_DECISION_ERROR;
      case DECISION_EVALUATION_ERROR -> DECISION_EVALUATION_ERROR;
      case FORM_NOT_FOUND -> FORM_NOT_FOUND;
      case RESOURCE_NOT_FOUND -> RESOURCE_NOT_FOUND;
    };
  }
}

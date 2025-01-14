/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceRequestValidator {
  private final CreateBatchOperationRequestValidator createBatchOperationRequestValidator;

  private final CreateRequestOperationValidator createRequestOperationValidator;

  public ProcessInstanceRequestValidator(
      @NotNull final CreateRequestOperationValidator createRequestOperationValidator,
      @NotNull final CreateBatchOperationRequestValidator createBatchOperationRequestValidator) {
    this.createRequestOperationValidator = createRequestOperationValidator;
    this.createBatchOperationRequestValidator = createBatchOperationRequestValidator;
  }

  public void validateFlowNodeStatisticsRequest(final ListViewQueryDto request) {
    final List<Long> processDefinitionKeys =
        CollectionUtil.toSafeListOfLongs(request.getProcessIds());
    if ((processDefinitionKeys != null && processDefinitionKeys.size() == 1)
        == (request.getBpmnProcessId() != null && request.getProcessVersion() != null)) {
      throw new InvalidRequestException(
          "Exactly one process must be specified in the request (via processIds or bpmnProcessId/version).");
    }
  }

  public void validateFlowNodeMetadataRequest(final FlowNodeMetadataRequestDto request) {
    if (request.getFlowNodeId() == null
        && request.getFlowNodeType() == null
        && request.getFlowNodeInstanceId() == null) {
      throw new InvalidRequestException(
          "At least flowNodeId or flowNodeInstanceId must be specified in the request.");
    }
    if (request.getFlowNodeId() != null && request.getFlowNodeInstanceId() != null) {
      throw new InvalidRequestException(
          "Only one of flowNodeId or flowNodeInstanceId must be specified in the request.");
    }
  }

  public void validateVariableRequest(final VariableRequestDto request) {
    if (request.getScopeId() == null) {
      throw new InvalidRequestException("ScopeId must be specified in the request.");
    }
  }

  public void validateListenerRequest(final ListenerRequestDto request) {
    if (request.getPageSize() == null
        || (request.getFlowNodeId() == null && request.getFlowNodeInstanceId() == null)) {
      throw new InvalidRequestException(
          "'pageSize' and either 'flowNodeId' or 'flowNodeInstanceId' must be specified in the request.");
    }
    if (request.getFlowNodeId() != null && request.getFlowNodeInstanceId() != null) {
      throw new InvalidRequestException(
          "Only one of 'flowNodeId' or 'flowNodeInstanceId' must be specified in the request.");
    }
    final ListenerType listenerTypeFilter = request.getListenerTypeFilter();
    if (listenerTypeFilter != null
        && listenerTypeFilter != ListenerType.EXECUTION_LISTENER
        && listenerTypeFilter != ListenerType.TASK_LISTENER) {
      throw new InvalidRequestException(
          "'listenerTypeFilter' only allows for values: ["
              + "null, "
              + ListenerType.EXECUTION_LISTENER
              + ", "
              + ListenerType.TASK_LISTENER
              + "]");
    }
  }

  public void validateCreateBatchOperationRequest(
      final CreateBatchOperationRequestDto batchOperationRequest) {
    createBatchOperationRequestValidator.validate(batchOperationRequest);
  }

  public void validateCreateOperationRequest(
      final CreateOperationRequestDto operationRequest, final String processInstanceId) {
    createRequestOperationValidator.validate(operationRequest, processInstanceId);
  }
}

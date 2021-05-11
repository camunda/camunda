/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;

public class OperationDto {

  private String id;

  private String batchOperationId;

  private OperationType type;

  private OperationState state;

  private String errorMessage;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getBatchOperationId() {
    return batchOperationId;
  }

  public OperationDto setBatchOperationId(final String batchOperationId) {
    this.batchOperationId = batchOperationId;
    return this;
  }

  public OperationType getType() {
    return type;
  }

  public void setType(OperationType type) {
    this.type = type;
  }

  public OperationState getState() {
    return state;
  }

  public void setState(OperationState state) {
    this.state = state;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public static OperationDto createFrom(OperationEntity operationEntity) {
    if (operationEntity == null) {
      return null;
    }
    OperationDto operation = new OperationDto();
    operation.setId(operationEntity.getId());
    operation.setType(operationEntity.getType());
    operation.setState(operationEntity.getState());
    operation.setErrorMessage(operationEntity.getErrorMessage());
    operation.setBatchOperationId(operationEntity.getBatchOperationId());
    return operation;
  }

  public static List<OperationDto> createFrom(List<OperationEntity> operationEntities) {
    List<OperationDto> result = new ArrayList<>();
    if (operationEntities != null) {
      for (OperationEntity operationEntity: operationEntities) {
        if (operationEntity != null) {
          result.add(createFrom(operationEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OperationDto that = (OperationDto) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(batchOperationId, that.batchOperationId) &&
        type == that.type &&
        state == that.state &&
        Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, batchOperationId, type, state, errorMessage);
  }
}

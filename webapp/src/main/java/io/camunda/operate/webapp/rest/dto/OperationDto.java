/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;

import java.util.ArrayList;
import java.util.List;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;

public class OperationDto {

  private String id;

  private OperationType type;

  private OperationState state;

  private String errorMessage;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OperationDto that = (OperationDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (type != that.type)
      return false;
    if (state != that.state)
      return false;
    return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    return result;
  }
}

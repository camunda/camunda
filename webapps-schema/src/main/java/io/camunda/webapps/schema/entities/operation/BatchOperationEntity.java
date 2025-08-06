/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BatchOperationEntity extends AbstractExporterEntity<BatchOperationEntity> {

  private String name;
  private OperationType type;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String username;

  private Integer instancesCount = 0;
  private Integer operationsTotalCount = 0;
  // Legacy - Contains all operations (completed + failed)
  private Integer operationsFinishedCount = 0;

  // new fields for batch operation in zeebe engine
  private BatchOperationState state;
  private Integer operationsFailedCount = 0; // Just failed / rejected operations
  private Integer operationsCompletedCount = 0; // Just successfully completed operations
  private List<BatchOperationErrorEntity> errors = List.of();

  @JsonIgnore private Object[] sortValues;

  public String getName() {
    return name;
  }

  public BatchOperationEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public OperationType getType() {
    return type;
  }

  public BatchOperationEntity setType(final OperationType type) {
    this.type = type;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public BatchOperationEntity setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public BatchOperationState getState() {
    return state;
  }

  public BatchOperationEntity setState(final BatchOperationState state) {
    this.state = state;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public BatchOperationEntity setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public BatchOperationEntity setUsername(final String username) {
    this.username = username;
    return this;
  }

  public Integer getInstancesCount() {
    return instancesCount;
  }

  public BatchOperationEntity setInstancesCount(final Integer instancesCount) {
    this.instancesCount = instancesCount;
    return this;
  }

  public Integer getOperationsTotalCount() {
    return operationsTotalCount;
  }

  public BatchOperationEntity setOperationsTotalCount(final Integer operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
    return this;
  }

  public Integer getOperationsFinishedCount() {
    return operationsFinishedCount;
  }

  public BatchOperationEntity setOperationsFinishedCount(final Integer operationsFinishedCount) {
    this.operationsFinishedCount = operationsFinishedCount;
    return this;
  }

  public Integer getOperationsFailedCount() {
    return operationsFailedCount;
  }

  public BatchOperationEntity setOperationsFailedCount(final Integer operationsFailedCount) {
    this.operationsFailedCount = operationsFailedCount;
    return this;
  }

  public Integer getOperationsCompletedCount() {
    return operationsCompletedCount;
  }

  public BatchOperationEntity setOperationsCompletedCount(final Integer operationsCompletedCount) {
    this.operationsCompletedCount = operationsCompletedCount;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public BatchOperationEntity setSortValues(final Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public List<BatchOperationErrorEntity> getErrors() {
    return errors;
  }

  public BatchOperationEntity setErrors(final List<BatchOperationErrorEntity> errors) {
    this.errors = errors;
    return this;
  }

  public BatchOperationEntity withGeneratedId() {
    setId(UUID.randomUUID().toString());
    return this;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (username != null ? username.hashCode() : 0);
    result = 31 * result + (instancesCount != null ? instancesCount.hashCode() : 0);
    result = 31 * result + (operationsTotalCount != null ? operationsTotalCount.hashCode() : 0);
    result = 31 * result + (state != null ? state.hashCode() : 0);
    result =
        31 * result + (operationsFinishedCount != null ? operationsFinishedCount.hashCode() : 0);
    result =
        31 * result + (operationsCompletedCount != null ? operationsCompletedCount.hashCode() : 0);
    result = 31 * result + (operationsFailedCount != null ? operationsFailedCount.hashCode() : 0);
    result = 31 * result + (errors != null ? errors.hashCode() : 0);
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
    if (!super.equals(o)) {
      return false;
    }

    final BatchOperationEntity that = (BatchOperationEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null) {
      return false;
    }
    if (type != that.type) {
      return false;
    }
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
      return false;
    }
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) {
      return false;
    }
    if (username != null ? !username.equals(that.username) : that.username != null) {
      return false;
    }
    if (instancesCount != null
        ? !instancesCount.equals(that.instancesCount)
        : that.instancesCount != null) {
      return false;
    }
    if (operationsTotalCount != null
        ? !operationsTotalCount.equals(that.operationsTotalCount)
        : that.operationsTotalCount != null) {
      return false;
    }
    if (!Objects.equals(state, that.state)) {
      return false;
    }
    if (!Objects.equals(operationsCompletedCount, that.operationsCompletedCount)) {
      return false;
    }
    if (!Objects.equals(operationsFailedCount, that.operationsFailedCount)) {
      return false;
    }
    if (!Objects.equals(errors, that.errors)) {
      return false;
    }

    return operationsFinishedCount != null
        ? operationsFinishedCount.equals(that.operationsFinishedCount)
        : that.operationsFinishedCount == null;
  }

  @Override
  public String toString() {
    return "BatchOperationEntity{"
        + "id='"
        + getId()
        + "', name='"
        + name
        + '\''
        + ", type="
        + type
        + ", startDate="
        + startDate
        + ", endDate="
        + endDate
        + ", username='"
        + username
        + '\''
        + ", instancesCount="
        + instancesCount
        + ", operationsTotalCount="
        + operationsTotalCount
        + ", operationsFinishedCount="
        + operationsFinishedCount
        + ", state="
        + state
        + ", operationsFailedCount="
        + operationsFailedCount
        + ", operationsCompletedCount="
        + operationsCompletedCount
        + ", errors="
        + errors
        + '}';
  }

  public enum BatchOperationState {
    CREATED,
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    CANCELED
  }
}

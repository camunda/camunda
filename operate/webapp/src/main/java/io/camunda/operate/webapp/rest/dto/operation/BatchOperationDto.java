/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BatchOperationDto {

  private String id;

  private String name;
  private OperationTypeDto type;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;

  private Integer instancesCount = 0;
  private Integer operationsTotalCount = 0;
  private Integer operationsFinishedCount = 0;
  private Integer failedOperationsCount = 0;
  private Integer completedOperationsCount = 0;

  /**
   * Sort values, define the position of batch operation in the list and may be used to search for
   * previous of following page.
   */
  private SortValuesWrapper[] sortValues;

  public static BatchOperationDto createFrom(
      final BatchOperationEntity batchOperationEntity, final ObjectMapper objectMapper) {
    return new BatchOperationDto()
        .setId(batchOperationEntity.getId())
        .setName(batchOperationEntity.getName())
        .setType(OperationTypeDto.getType(batchOperationEntity.getType()))
        .setStartDate(batchOperationEntity.getStartDate())
        .setEndDate(batchOperationEntity.getEndDate())
        .setInstancesCount(batchOperationEntity.getInstancesCount())
        .setOperationsTotalCount(batchOperationEntity.getOperationsTotalCount())
        .setOperationsFinishedCount(batchOperationEntity.getOperationsFinishedCount())
        // convert to String[]
        .setSortValues(
            SortValuesWrapper.createFrom(batchOperationEntity.getSortValues(), objectMapper));
  }

  public static List<BatchOperationDto> createFrom(
      final List<BatchOperationEntity> batchOperationEntities, final ObjectMapper objectMapper) {
    if (batchOperationEntities == null) {
      return new ArrayList<>();
    }
    return batchOperationEntities.stream()
        .filter(item -> item != null)
        .map(item -> createFrom(item, objectMapper))
        .collect(Collectors.toList());
  }

  public String getName() {
    return name;
  }

  public BatchOperationDto setName(final String name) {
    this.name = name;
    return this;
  }

  public OperationTypeDto getType() {
    return type;
  }

  public BatchOperationDto setType(final OperationTypeDto type) {
    this.type = type;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public BatchOperationDto setStartDate(final OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public BatchOperationDto setEndDate(final OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public Integer getInstancesCount() {
    return instancesCount;
  }

  public BatchOperationDto setInstancesCount(final Integer instancesCount) {
    this.instancesCount = instancesCount;
    return this;
  }

  public Integer getOperationsTotalCount() {
    return operationsTotalCount;
  }

  public BatchOperationDto setOperationsTotalCount(final Integer operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
    return this;
  }

  public Integer getOperationsFinishedCount() {
    return operationsFinishedCount;
  }

  public BatchOperationDto setOperationsFinishedCount(final Integer operationsFinishedCount) {
    this.operationsFinishedCount = operationsFinishedCount;
    return this;
  }

  public Integer getFailedOperationsCount() {
    return failedOperationsCount;
  }

  public BatchOperationDto setFailedOperationsCount(final Integer failedOperationsCount) {
    this.failedOperationsCount = failedOperationsCount;
    return this;
  }

  public Integer getCompletedOperationsCount() {
    return completedOperationsCount;
  }

  public BatchOperationDto setCompletedOperationsCount(final Integer completedOperationsCount) {
    this.completedOperationsCount = completedOperationsCount;
    return this;
  }

  public String getId() {
    return id;
  }

  public BatchOperationDto setId(final String id) {
    this.id = id;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public BatchOperationDto setSortValues(final SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
    result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
    result = 31 * result + (instancesCount != null ? instancesCount.hashCode() : 0);
    result = 31 * result + (operationsTotalCount != null ? operationsTotalCount.hashCode() : 0);
    result =
        31 * result + (operationsFinishedCount != null ? operationsFinishedCount.hashCode() : 0);
    result =
        31 * result + (completedOperationsCount != null ? completedOperationsCount.hashCode() : 0);
    result = 31 * result + (failedOperationsCount != null ? failedOperationsCount.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(sortValues);
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

    final BatchOperationDto that = (BatchOperationDto) o;

    if (!Objects.equals(id, that.id)) {
      return false;
    }
    if (!Objects.equals(name, that.name)) {
      return false;
    }
    if (type != that.type) {
      return false;
    }
    if (!Objects.equals(startDate, that.startDate)) {
      return false;
    }
    if (!Objects.equals(endDate, that.endDate)) {
      return false;
    }
    if (!Objects.equals(instancesCount, that.instancesCount)) {
      return false;
    }
    if (!Objects.equals(operationsTotalCount, that.operationsTotalCount)) {
      return false;
    }
    if (!Objects.equals(operationsFinishedCount, that.operationsFinishedCount)) {
      return false;
    }
    if (!Objects.equals(failedOperationsCount, that.failedOperationsCount)) {
      return false;
    }
    if (!Objects.equals(completedOperationsCount, that.completedOperationsCount)) {
      return false;
    }
    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    return Arrays.equals(sortValues, that.sortValues);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.webapp.rest.dto.listview.SortValuesWrapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

  /**
   * Sort values, define the position of batch operation in the list and may be used to search for previous of following page.
   */
  private SortValuesWrapper[] sortValues;

  public String getName() {
    return name;
  }

  public BatchOperationDto setName(String name) {
    this.name = name;
    return this;
  }

  public OperationTypeDto getType() {
    return type;
  }

  public BatchOperationDto setType(OperationTypeDto type) {
    this.type = type;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public BatchOperationDto setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public BatchOperationDto setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public Integer getInstancesCount() {
    return instancesCount;
  }

  public BatchOperationDto setInstancesCount(Integer instancesCount) {
    this.instancesCount = instancesCount;
    return this;
  }

  public Integer getOperationsTotalCount() {
    return operationsTotalCount;
  }

  public BatchOperationDto setOperationsTotalCount(Integer operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
    return this;
  }

  public Integer getOperationsFinishedCount() {
    return operationsFinishedCount;
  }

  public BatchOperationDto setOperationsFinishedCount(Integer operationsFinishedCount) {
    this.operationsFinishedCount = operationsFinishedCount;
    return this;
  }

  public String getId() {
    return id;
  }

  public BatchOperationDto setId(String id) {
    this.id = id;
    return this;
  }

  public SortValuesWrapper[] getSortValues() {
    return sortValues;
  }

  public BatchOperationDto setSortValues(SortValuesWrapper[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public static BatchOperationDto createFrom(final BatchOperationEntity batchOperationEntity, ObjectMapper objectMapper) {
    return new BatchOperationDto()
        .setId(batchOperationEntity.getId())
        .setName(batchOperationEntity.getName())
        .setType(OperationTypeDto.getType(batchOperationEntity.getType()))
        .setStartDate(batchOperationEntity.getStartDate())
        .setEndDate(batchOperationEntity.getEndDate())
        .setInstancesCount(batchOperationEntity.getInstancesCount())
        .setOperationsTotalCount(batchOperationEntity.getOperationsTotalCount())
        .setOperationsFinishedCount(batchOperationEntity.getOperationsFinishedCount())
        //convert to String[]
        .setSortValues(SortValuesWrapper.createFrom(batchOperationEntity.getSortValues(), objectMapper));
  }

  public static List<BatchOperationDto> createFrom(
      List<BatchOperationEntity> batchOperationEntities, ObjectMapper objectMapper) {
    if (batchOperationEntities == null) {
      return new ArrayList<>();
    }
    return batchOperationEntities.stream().filter(item -> item != null)
        .map(item -> createFrom(item, objectMapper))
        .collect(Collectors.toList());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    BatchOperationDto that = (BatchOperationDto) o;

    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (type != that.type)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (instancesCount != null ? !instancesCount.equals(that.instancesCount) : that.instancesCount != null)
      return false;
    if (operationsTotalCount != null ? !operationsTotalCount.equals(that.operationsTotalCount) : that.operationsTotalCount != null)
      return false;
    if (operationsFinishedCount != null ? !operationsFinishedCount.equals(that.operationsFinishedCount) : that.operationsFinishedCount != null)
      return false;
    // Probably incorrect - comparing Object[] arrays with Arrays.equals
    return Arrays.equals(sortValues, that.sortValues);

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
    result = 31 * result + (operationsFinishedCount != null ? operationsFinishedCount.hashCode() : 0);
    result = 31 * result + Arrays.hashCode(sortValues);
    return result;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class BatchOperationEntity extends OperateEntity<BatchOperationEntity> {

  private String name;
  private OperationType type;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String username;

  private Integer instancesCount = 0;
  private Integer operationsTotalCount = 0;
  private Integer operationsFinishedCount = 0;

  @JsonIgnore
  private Object[] sortValues;

  public String getName() {
    return name;
  }

  public BatchOperationEntity setName(String name) {
    this.name = name;
    return this;
  }

  public OperationType getType() {
    return type;
  }

  public BatchOperationEntity setType(OperationType type) {
    this.type = type;
    return this;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public BatchOperationEntity setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
    return this;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public BatchOperationEntity setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public BatchOperationEntity setUsername(String username) {
    this.username = username;
    return this;
  }

  public Integer getInstancesCount() {
    return instancesCount;
  }

  public BatchOperationEntity setInstancesCount(Integer instancesCount) {
    this.instancesCount = instancesCount;
    return this;
  }

  public Integer getOperationsTotalCount() {
    return operationsTotalCount;
  }

  public BatchOperationEntity setOperationsTotalCount(Integer operationsTotalCount) {
    this.operationsTotalCount = operationsTotalCount;
    return this;
  }

  public Integer getOperationsFinishedCount() {
    return operationsFinishedCount;
  }

  public BatchOperationEntity setOperationsFinishedCount(Integer operationsFinishedCount) {
    this.operationsFinishedCount = operationsFinishedCount;
    return this;
  }

  public Object[] getSortValues() {
    return sortValues;
  }

  public BatchOperationEntity setSortValues(Object[] sortValues) {
    this.sortValues = sortValues;
    return this;
  }

  public void generateId() {
    setId(UUID.randomUUID().toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    BatchOperationEntity that = (BatchOperationEntity) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    if (type != that.type)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (username != null ? !username.equals(that.username) : that.username != null)
      return false;
    if (instancesCount != null ? !instancesCount.equals(that.instancesCount) : that.instancesCount != null)
      return false;
    if (operationsTotalCount != null ? !operationsTotalCount.equals(that.operationsTotalCount) : that.operationsTotalCount != null)
      return false;
    return operationsFinishedCount != null ? operationsFinishedCount.equals(that.operationsFinishedCount) : that.operationsFinishedCount == null;

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
    result = 31 * result + (operationsFinishedCount != null ? operationsFinishedCount.hashCode() : 0);
    return result;
  }
}

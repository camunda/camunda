/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.entities;

import java.time.OffsetDateTime;
import java.util.UUID;

public class BatchOperationEntity extends OperateEntity {

  private String name;
  private OperationType type;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private String username;

  private Integer instancesCount = 0;
  private Integer operationsCount = 0;
  private Integer finishedCount = 0;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public OperationType getType() {
    return type;
  }

  public void setType(OperationType type) {
    this.type = type;
  }

  public OffsetDateTime getStartDate() {
    return startDate;
  }

  public void setStartDate(OffsetDateTime startDate) {
    this.startDate = startDate;
  }

  public OffsetDateTime getEndDate() {
    return endDate;
  }

  public void setEndDate(OffsetDateTime endDate) {
    this.endDate = endDate;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public Integer getInstancesCount() {
    return instancesCount;
  }

  public void setInstancesCount(Integer instancesCount) {
    this.instancesCount = instancesCount;
  }

  public Integer getOperationsCount() {
    return operationsCount;
  }

  public void setOperationsCount(Integer operationsCount) {
    this.operationsCount = operationsCount;
  }

  public Integer getFinishedCount() {
    return finishedCount;
  }

  public void setFinishedCount(Integer finishedCount) {
    this.finishedCount = finishedCount;
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
    if (type != null ? !type.equals(that.type) : that.type != null)
      return false;
    if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null)
      return false;
    if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null)
      return false;
    if (username != null ? !username.equals(that.username) : that.username != null)
      return false;
    if (instancesCount != null ? !instancesCount.equals(that.instancesCount) : that.instancesCount != null)
      return false;
    if (operationsCount != null ? !operationsCount.equals(that.operationsCount) : that.operationsCount != null)
      return false;
    return finishedCount != null ? finishedCount.equals(that.finishedCount) : that.finishedCount == null;

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
    result = 31 * result + (operationsCount != null ? operationsCount.hashCode() : 0);
    result = 31 * result + (finishedCount != null ? finishedCount.hashCode() : 0);
    return result;
  }
}

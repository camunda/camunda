/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operation;

import io.camunda.webapps.schema.entities.PartitionedEntity;
import java.util.Objects;

public class BatchOperationErrorEntity implements PartitionedEntity<BatchOperationErrorEntity> {

  private int partitionId;
  private String type;
  private String message;

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public BatchOperationErrorEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getType() {
    return type;
  }

  public BatchOperationErrorEntity setType(final String type) {
    this.type = type;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public BatchOperationErrorEntity setMessage(final String message) {
    this.message = message;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Integer.hashCode(partitionId);
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (message != null ? message.hashCode() : 0);
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
    final BatchOperationErrorEntity that = (BatchOperationErrorEntity) o;
    return partitionId == that.partitionId
        && Objects.equals(type, that.type)
        && Objects.equals(message, that.message);
  }
}

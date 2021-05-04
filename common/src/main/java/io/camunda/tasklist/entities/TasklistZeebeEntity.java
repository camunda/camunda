/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.entities;

public abstract class TasklistZeebeEntity<T extends TasklistZeebeEntity<T>>
    extends TasklistEntity<T> {

  private long key;

  private int partitionId;

  public long getKey() {
    return key;
  }

  public T setKey(long key) {
    this.key = key;
    return (T) this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public T setPartitionId(int partitionId) {
    this.partitionId = partitionId;
    return (T) this;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (int) (key ^ (key >>> 32));
    result = 31 * result + partitionId;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }

    final TasklistZeebeEntity<?> that = (TasklistZeebeEntity<?>) o;

    if (key != that.key) {
      return false;
    }
    return partitionId == that.partitionId;
  }
}

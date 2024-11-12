/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;

public abstract class OperateZeebeEntity<T extends OperateZeebeEntity<T>>
    extends AbstractExporterEntity<T> {

  private long key;

  private int partitionId;

  public long getKey() {
    return key;
  }

  public T setKey(final long key) {
    this.key = key;
    return (T) this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public T setPartitionId(final int partitionId) {
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

    final OperateZeebeEntity<T> that = (OperateZeebeEntity<T>) o;

    if (key != that.key) {
      return false;
    }
    return partitionId == that.partitionId;
  }
}

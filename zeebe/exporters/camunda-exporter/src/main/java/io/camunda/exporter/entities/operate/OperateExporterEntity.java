/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.entities.operate;

import io.camunda.exporter.entities.ExporterEntity;
import java.util.Objects;

public abstract class OperateExporterEntity<T extends OperateExporterEntity<T>>
    implements ExporterEntity<T> {
  private String id;
  private long key;
  private int partitionId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public T setId(final String id) {
    this.id = id;
    return (T) this;
  }

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
    return Objects.hash(key, partitionId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final OperateExporterEntity<?> that = (OperateExporterEntity<?>) o;
    return key == that.key && partitionId == that.partitionId;
  }
}

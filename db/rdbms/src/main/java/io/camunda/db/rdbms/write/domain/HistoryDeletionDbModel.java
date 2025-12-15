/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;

public record HistoryDeletionDbModel(
    Long resourceKey,
    HistoryDeletionType resourceType,
    Long batchOperationKey,
    Integer partitionId) {

  public static final String ID_PATTERN = "%s_%s";

  public String getId() {
    return String.format(ID_PATTERN, batchOperationKey, resourceKey);
  }

  public static class Builder implements ObjectBuilder<HistoryDeletionDbModel> {

    private long resourceKey;
    private HistoryDeletionType resourceType;
    private long batchOperationKey;
    private int partitionId;

    public Builder resourceKey(final long resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    public Builder resourceType(final HistoryDeletionType resourceType) {
      this.resourceType = resourceType;
      return this;
    }

    public Builder batchOperationKey(final long batchOperationKey) {
      this.batchOperationKey = batchOperationKey;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    @Override
    public HistoryDeletionDbModel build() {
      return new HistoryDeletionDbModel(resourceKey, resourceType, batchOperationKey, partitionId);
    }
  }
}

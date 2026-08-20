/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record HistoryDeletionDbModel(
    Long resourceKey,
    HistoryDeletionTypeDbModel resourceType,
    Long batchOperationKey,
    Integer partitionId) {

  public static final String ID_PATTERN = "%s_%s";

  public String getId() {
    return String.format(ID_PATTERN, batchOperationKey, resourceKey);
  }

  public static class Builder implements ObjectBuilder<HistoryDeletionDbModel> {

    private long resourceKey;
    private HistoryDeletionTypeDbModel resourceType;
    private long batchOperationKey;
    private int partitionId;

    public Builder resourceKey(final long resourceKey) {
      this.resourceKey = resourceKey;
      return this;
    }

    public Builder resourceType(final HistoryDeletionTypeDbModel resourceType) {
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

  public enum HistoryDeletionTypeDbModel {
    PROCESS_INSTANCE,
    PROCESS_DEFINITION,
    DECISION_INSTANCE,
    DECISION_REQUIREMENTS
  }
}

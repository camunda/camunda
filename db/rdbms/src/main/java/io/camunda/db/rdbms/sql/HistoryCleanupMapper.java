/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;

public interface HistoryCleanupMapper {

  int cleanupHistory(CleanupHistoryDto dto);

  record CleanupHistoryDto(int partitionId, OffsetDateTime cleanupDate, int limit) {

    public static class Builder implements ObjectBuilder<CleanupHistoryDto> {

      private int partitionId;
      private OffsetDateTime cleanupDate;
      private int limit;

      public Builder partitionId(final int partitionId) {
        this.partitionId = partitionId;
        return this;
      }

      public Builder cleanupDate(final OffsetDateTime cleanupDate) {
        this.cleanupDate = cleanupDate;
        return this;
      }

      public Builder limit(final int limit) {
        this.limit = limit;
        return this;
      }

      @Override
      public CleanupHistoryDto build() {
        return new CleanupHistoryDto(partitionId, cleanupDate, limit);
      }
    }
  }
}

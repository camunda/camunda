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

public interface ProcessBasedHistoryCleanupMapper extends HistoryCleanupMapper {

  int updateHistoryCleanupDate(UpdateHistoryCleanupDateDto dto);

  record UpdateHistoryCleanupDateDto(long processInstanceKey, OffsetDateTime historyCleanupDate) {

    public static class Builder implements ObjectBuilder<UpdateHistoryCleanupDateDto> {

      private long processInstanceKey;
      private OffsetDateTime historyCleanupDate;

      public Builder processInstanceKey(final long processInstanceKey) {
        this.processInstanceKey = processInstanceKey;
        return this;
      }

      public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
        this.historyCleanupDate = historyCleanupDate;
        return this;
      }

      @Override
      public UpdateHistoryCleanupDateDto build() {
        return new UpdateHistoryCleanupDateDto(processInstanceKey, historyCleanupDate);
      }
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.write.domain.AuthorizationDbModel.Builder;
import io.camunda.db.rdbms.write.domain.Copyable;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface ProcessBasedHistoryCleanupMapper extends HistoryCleanupMapper {

  int updateHistoryCleanupDate(UpdateHistoryCleanupDateDto dto);

  record UpdateHistoryCleanupDateDto(
      List<Long> processInstanceKeys, OffsetDateTime historyCleanupDate)
      implements Copyable<UpdateHistoryCleanupDateDto> {

    @Override
    public UpdateHistoryCleanupDateDto copy(
        final Function<
                ObjectBuilder<UpdateHistoryCleanupDateDto>,
                ObjectBuilder<UpdateHistoryCleanupDateDto>>
            copyFunction) {
      return copyFunction
          .apply(
              new Builder()
                  .processInstanceKeys(new ArrayList<>(processInstanceKeys))
                  .historyCleanupDate(historyCleanupDate))
          .build();
    }

    public static class Builder implements ObjectBuilder<UpdateHistoryCleanupDateDto> {

      private List<Long> processInstanceKeys = new ArrayList<>();
      private OffsetDateTime historyCleanupDate;

      public Builder processInstanceKey(final long processInstanceKey) {
        processInstanceKeys.add(processInstanceKey);
        return this;
      }

      public Builder processInstanceKeys(final List<Long> processInstanceKeys) {
        this.processInstanceKeys = processInstanceKeys;
        return this;
      }

      public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
        this.historyCleanupDate = historyCleanupDate;
        return this;
      }

      @Override
      public UpdateHistoryCleanupDateDto build() {
        return new UpdateHistoryCleanupDateDto(processInstanceKeys, historyCleanupDate);
      }
    }
  }
}

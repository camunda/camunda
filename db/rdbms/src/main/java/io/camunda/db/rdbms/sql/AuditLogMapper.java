/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.AuditLogDbQuery;
import io.camunda.db.rdbms.write.domain.AuditLogDbModel;
import io.camunda.db.rdbms.write.domain.Copyable;
import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface AuditLogMapper
    extends ProcessInstanceDependantMapper, ProcessBasedHistoryCleanupMapper, HistoryCleanupMapper {

  void insert(AuditLogDbModel auditLog);

  Long count(AuditLogDbQuery filter);

  List<AuditLogDbModel> search(AuditLogDbQuery filter);

  int updateAuditLogEntityHistoryCleanupDate(UpdateHistoryCleanupDateDto dto);

  record UpdateHistoryCleanupDateDto(List<String> entityKeys, OffsetDateTime historyCleanupDate)
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
                  .entityKeys(new ArrayList<>(entityKeys))
                  .historyCleanupDate(historyCleanupDate))
          .build();
    }

    public static class Builder implements ObjectBuilder<UpdateHistoryCleanupDateDto> {

      private List<String> entityKeys = new ArrayList<>();
      private OffsetDateTime historyCleanupDate;

      public Builder entityKey(final String entityKey) {
        entityKeys.add(entityKey);
        return this;
      }

      public Builder entityKeys(final List<String> entityKeys) {
        this.entityKeys = entityKeys;
        return this;
      }

      public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
        this.historyCleanupDate = historyCleanupDate;
        return this;
      }

      @Override
      public UpdateHistoryCleanupDateDto build() {
        return new UpdateHistoryCleanupDateDto(entityKeys, historyCleanupDate);
      }
    }
  }
}

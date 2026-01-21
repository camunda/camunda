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
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public interface AuditLogMapper extends ProcessInstanceDependantMapper {

  void insert(BatchInsertAuditLogsDto dto);

  Long count(AuditLogDbQuery filter);

  List<AuditLogDbModel> search(AuditLogDbQuery filter);

  int deleteProcessDefinitionRelatedData(List<Long> processDefinitionKeys, int limit);

  record BatchInsertAuditLogsDto(List<AuditLogDbModel> auditLogs)
      implements BatchInsertDto<BatchInsertAuditLogsDto, AuditLogDbModel> {

    @Override
    public BatchInsertAuditLogsDto withAdditionalDbModel(final AuditLogDbModel auditLog) {
      return new Builder().auditLogs(new ArrayList<>(auditLogs)).auditLog(auditLog).build();
    }

    @Override
    public BatchInsertAuditLogsDto copy(
        final Function<
                ObjectBuilder<BatchInsertAuditLogsDto>, ObjectBuilder<BatchInsertAuditLogsDto>>
            copyFunction) {
      return copyFunction.apply(new Builder().auditLogs(new ArrayList<>(auditLogs))).build();
    }

    public static class Builder implements ObjectBuilder<BatchInsertAuditLogsDto> {

      private List<AuditLogDbModel> auditLogs = new ArrayList<>();

      public Builder auditLog(final AuditLogDbModel auditLog) {
        auditLogs.add(auditLog);
        return this;
      }

      public Builder auditLogs(final List<AuditLogDbModel> auditLogs) {
        this.auditLogs = auditLogs;
        return this;
      }

      @Override
      public BatchInsertAuditLogsDto build() {
        return new BatchInsertAuditLogsDto(auditLogs);
      }
    }
  }
}

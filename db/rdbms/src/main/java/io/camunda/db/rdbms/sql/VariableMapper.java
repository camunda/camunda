/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.VariableDbQuery;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionVariableNameLookupDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.queue.BatchInsertDto;
import io.camunda.search.entities.VariableEntity;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface VariableMapper extends ProcessInstanceDependantMapper {

  void insert(BatchInsertDto<VariableDbModel> dto);

  void update(VariableDbModel variable);

  void migrateToProcess(MigrateToProcessDto dto);

  Long count(VariableDbQuery filter);

  List<VariableEntity> search(VariableDbQuery filter);

  // --- PROCESS_DEF_VAR_NAME_LOOKUP ---

  /**
   * Inserts a new lookup entry if no entry already exists for the given (processDefinitionKey,
   * varName) combination. No-op on conflict.
   */
  void insertLookupIfNotExists(ProcessDefinitionVariableNameLookupDbModel model);

  /** Returns all variable names recorded for the given process definition key. */
  List<String> findLookupVariableNames(@Param("processDefinitionKey") long processDefinitionKey);

  /** Deletes all lookup entries for the given process definition keys. */
  void deleteLookupByProcessDefinitionKeys(List<Long> processDefinitionKeys, int limit);

  record MigrateToProcessDto(Long variableKey, String processDefinitionId) {

    public static class Builder implements ObjectBuilder<MigrateToProcessDto> {

      private Long variableKey;
      private String processDefinitionId;

      public Builder variableKey(final Long variableKey) {
        this.variableKey = variableKey;
        return this;
      }

      public Builder processDefinitionId(final String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
        return this;
      }

      @Override
      public MigrateToProcessDto build() {
        return new MigrateToProcessDto(variableKey, processDefinitionId);
      }
    }
  }
}

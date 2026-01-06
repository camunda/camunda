/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.VariableDbQuery;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.search.entities.VariableEntity;
import io.camunda.util.ObjectBuilder;
import java.util.List;

public interface VariableMapper extends ProcessBasedHistoryCleanupMapper {

  void insert(VariableDbModel variable);

  void update(VariableDbModel variable);

  void migrateToProcess(MigrateToProcessDto dto);

  Long count(VariableDbQuery filter);

  List<VariableEntity> search(VariableDbQuery filter);

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

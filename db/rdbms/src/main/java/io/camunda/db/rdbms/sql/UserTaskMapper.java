/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql;

import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.util.ObjectBuilder;
import java.util.List;

public interface UserTaskMapper {

  void insert(UserTaskDbModel taskDbModel);

  void insertCandidateUsers(UserTaskDbModel taskDbModel);

  void insertCandidateGroups(UserTaskDbModel taskDbModel);

  void update(UserTaskDbModel taskDbModel);

  void deleteCandidateUsers(Long key);

  void deleteCandidateGroups(Long key);

  void migrateToProcess(MigrateToProcessDto dto);

  Long count(UserTaskDbQuery filter);

  List<UserTaskDbModel> search(UserTaskDbQuery filter);

  record MigrateToProcessDto(
      Long userTaskKey,
      Long processDefinitionKey,
      String processDefinitionId,
      String elementId,
      Integer processDefinitionVersion) {

    public static class Builder implements ObjectBuilder<MigrateToProcessDto> {

      private Long userTaskKey;
      private Long processDefinitionKey;
      private String processDefinitionId;
      private String elementId;
      private Integer processDefinitionVersion;

      public Builder userTaskKey(Long userTaskKey) {
        this.userTaskKey = userTaskKey;
        return this;
      }

      public Builder processDefinitionKey(Long processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
        return this;
      }

      public Builder processDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
        return this;
      }

      public Builder elementId(String elementId) {
        this.elementId = elementId;
        return this;
      }

      public Builder processDefinitionVersion(Integer processDefinitionVersion) {
        this.processDefinitionVersion = processDefinitionVersion;
        return this;
      }

      @Override
      public MigrateToProcessDto build() {
        return new MigrateToProcessDto(
            userTaskKey,
            processDefinitionKey,
            processDefinitionId,
            elementId,
            processDefinitionVersion);
      }
    }
  }
}

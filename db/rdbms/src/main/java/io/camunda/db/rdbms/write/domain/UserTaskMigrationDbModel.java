/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record UserTaskMigrationDbModel(
    Long userTaskKey,
    Long processDefinitionKey,
    String processDefinitionId,
    String elementId,
    Integer processDefinitionVersion) {

  public static class Builder implements ObjectBuilder<UserTaskMigrationDbModel> {

    private Long userTaskKey;
    private Long processDefinitionKey;
    private String processDefinitionId;
    private String elementId;
    private Integer processDefinitionVersion;

    public Builder userTaskKey(final Long userTaskKey) {
      this.userTaskKey = userTaskKey;
      return this;
    }

    public Builder processDefinitionKey(final Long processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
      return this;
    }

    public Builder elementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public Builder processDefinitionVersion(final Integer processDefinitionVersion) {
      this.processDefinitionVersion = processDefinitionVersion;
      return this;
    }

    @Override
    public UserTaskMigrationDbModel build() {
      return new UserTaskMigrationDbModel(
          userTaskKey,
          processDefinitionKey,
          processDefinitionId,
          elementId,
          processDefinitionVersion);
    }
  }
}

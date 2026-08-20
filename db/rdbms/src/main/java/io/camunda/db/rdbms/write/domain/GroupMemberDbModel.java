/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record GroupMemberDbModel(String groupId, String entityId, String entityType) {

  // create builder implementing ObjectBuilder
  public static class Builder implements ObjectBuilder<GroupMemberDbModel> {

    private String groupId;
    private String entityId;
    private String entityType;

    public Builder groupId(final String groupId) {
      this.groupId = groupId;
      return this;
    }

    public Builder entityId(final String entityId) {
      this.entityId = entityId;
      return this;
    }

    public Builder entityType(final String entityType) {
      this.entityType = entityType;
      return this;
    }

    @Override
    public GroupMemberDbModel build() {
      return new GroupMemberDbModel(groupId, entityId, entityType);
    }
  }
}

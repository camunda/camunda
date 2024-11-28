/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record RoleMemberDbModel(Long roleKey, Long entityKey, String entityType) {

  // create builder implementing ObjectBuilder
  public static class Builder implements ObjectBuilder<RoleMemberDbModel> {

    private Long roleKey;
    private Long entityKey;
    private String entityType;

    public Builder roleKey(Long roleKey) {
      this.roleKey = roleKey;
      return this;
    }

    public Builder entityKey(Long entityKey) {
      this.entityKey = entityKey;
      return this;
    }

    public Builder entityType(String entityType) {
      this.entityType = entityType;
      return this;
    }

    @Override
    public RoleMemberDbModel build() {
      return new RoleMemberDbModel(roleKey, entityKey, entityType);
    }
  }
}

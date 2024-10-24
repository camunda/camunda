/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

public record VariableDbModel(
    Long key, String name, String value, Long scopeKey, Long processInstanceKey, String tenantId) {

  public static class VariableDbModelBuilder {

    private Long key;
    private String name;
    private String value;
    private Long scopeKey;
    private Long processInstanceKey;
    private String tenantId;

    // Public constructor to initialize the builder
    public VariableDbModelBuilder() {}

    // Builder methods for each field
    public VariableDbModelBuilder key(final Long key) {
      this.key = key;
      return this;
    }

    public VariableDbModelBuilder value(final String value) {
      this.value = value;
      return this;
    }

    public VariableDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public VariableDbModelBuilder scopeKey(final Long scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    public VariableDbModelBuilder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public VariableDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    // Build method to create the record
    public VariableDbModel build() {
      return new VariableDbModel(key, name, value, scopeKey, processInstanceKey, tenantId);
    }
  }
}

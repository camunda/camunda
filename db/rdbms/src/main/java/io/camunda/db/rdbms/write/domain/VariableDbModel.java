/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static io.camunda.util.ValueTypeUtil.mapBoolean;

import io.camunda.search.entities.ValueTypeEnum;
import io.camunda.util.ValueTypeUtil;

public record VariableDbModel(
    Long key,
    String name,
    ValueTypeEnum type,
    Double doubleValue,
    Long longValue,
    String value,
    String fullValue,
    boolean isPreview,
    Long scopeKey,
    Long processInstanceKey,
    String tenantId) {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191; // TODO make configurable

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
      if (value != null && value.length() > DEFAULT_VARIABLE_SIZE_THRESHOLD) {
        return getModelWithPreview();
      } else {
        return switch (ValueTypeUtil.getValueType(value)) {
          case LONG -> getLongModel();
          case DOUBLE -> getDoubleModel();
          case BOOLEAN -> getModel(mapBoolean(value));
          default -> getModel(value);
        };
      }
    }

    private VariableDbModel getModel(final String value) {
      return new VariableDbModel(
          key,
          name,
          ValueTypeEnum.STRING,
          null,
          null,
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          tenantId);
    }

    private VariableDbModel getModelWithPreview() {
      return new VariableDbModel(
          key,
          name,
          ValueTypeEnum.STRING,
          null,
          null,
          value.substring(0, DEFAULT_VARIABLE_SIZE_THRESHOLD),
          value,
          true,
          scopeKey,
          processInstanceKey,
          tenantId);
    }

    private VariableDbModel getLongModel() {
      return new VariableDbModel(
          key,
          name,
          ValueTypeEnum.LONG,
          null,
          Long.parseLong(value),
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          tenantId);
    }

    private VariableDbModel getDoubleModel() {
      return new VariableDbModel(
          key,
          name,
          ValueTypeEnum.DOUBLE,
          Double.parseDouble(value),
          null,
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          tenantId);
    }
  }
}

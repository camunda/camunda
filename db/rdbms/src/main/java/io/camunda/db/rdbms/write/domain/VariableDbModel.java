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
import io.camunda.util.ObjectBuilder;
import io.camunda.util.ValueTypeUtil;
import java.util.function.Function;

public record VariableDbModel(
    Long variableKey,
    String name,
    ValueTypeEnum type,
    Double doubleValue,
    Long longValue,
    String value,
    String fullValue,
    boolean isPreview,
    Long scopeKey,
    Long processInstanceKey,
    String processDefinitionId,
    String tenantId) {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191; // TODO make configurable

  public VariableDbModel copy(
      final Function<ObjectBuilder<VariableDbModel>, ObjectBuilder<VariableDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new VariableDbModelBuilder()
                .variableKey(this.variableKey)
                .value(this.value)
                .name(this.name)
                .scopeKey(this.scopeKey)
                .processInstanceKey(this.processInstanceKey)
                .processDefinitionId(this.processDefinitionId)
                .tenantId(this.tenantId))
        .build();
  }

  public static class VariableDbModelBuilder implements ObjectBuilder<VariableDbModel> {

    private Long variableKey;
    private String name;
    private String value;
    private Long scopeKey;
    private Long processInstanceKey;
    private String processDefinitionId;
    private String tenantId;

    public VariableDbModelBuilder() {}

    public VariableDbModelBuilder variableKey(final Long variableKey) {
      this.variableKey = variableKey;
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

    public VariableDbModelBuilder processDefinitionId(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
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
          variableKey,
          name,
          ValueTypeEnum.STRING,
          null,
          null,
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId);
    }

    private VariableDbModel getModelWithPreview() {
      return new VariableDbModel(
          variableKey,
          name,
          ValueTypeEnum.STRING,
          null,
          null,
          value.substring(0, DEFAULT_VARIABLE_SIZE_THRESHOLD),
          value,
          true,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId);
    }

    private VariableDbModel getLongModel() {
      return new VariableDbModel(
          variableKey,
          name,
          ValueTypeEnum.LONG,
          null,
          Long.parseLong(value),
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId);
    }

    private VariableDbModel getDoubleModel() {
      return new VariableDbModel(
          variableKey,
          name,
          ValueTypeEnum.DOUBLE,
          Double.parseDouble(value),
          null,
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId);
    }
  }
}

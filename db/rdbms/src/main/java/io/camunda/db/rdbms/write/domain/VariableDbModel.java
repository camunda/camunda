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

import java.util.Objects;
import java.util.function.Function;

public final class VariableDbModel {

  public static final int DEFAULT_VARIABLE_SIZE_THRESHOLD = 8191; // TODO make configurable
  private final Long variableKey;
  private final String name;
  private final ValueTypeEnum type;
  private final Double doubleValue;
  private final Long longValue;
  private final String value;
  private final String fullValue;
  private final boolean isPreview;
  private final Long scopeKey;
  private final Long processInstanceKey;
  private final String processDefinitionId;
  private final String tenantId;
  private final String legacyId;
  private final String legacyProcessInstanceId;

  public VariableDbModel(Long variableKey,
                         String legacyId,
                         String legacyProcessInstanceId,
                         Long processInstanceKey,
                         String processDefinitionId,
                         Long scopeKey,
                         ValueTypeEnum type,
                         String name,
                         Double doubleValue,
                         Long longValue,
                         String value,
                         String fullValue,
                         String tenantId,
                         boolean isPreview) {
    this.variableKey = variableKey;
    this.name = name;
    this.type = type;
    this.doubleValue = doubleValue;
    this.longValue = longValue;
    this.value = value;
    this.fullValue = fullValue;
    this.isPreview = isPreview;
    this.scopeKey = scopeKey;
    this.processInstanceKey = processInstanceKey;
    this.processDefinitionId = processDefinitionId;
    this.tenantId = tenantId;
    this.legacyId = legacyId;
    this.legacyProcessInstanceId = legacyProcessInstanceId;
  }

  public VariableDbModel copy(final Function<ObjectBuilder<VariableDbModel>, ObjectBuilder<VariableDbModel>> builderFunction) {
    return builderFunction.apply(new VariableDbModelBuilder().legacyId(legacyId)
        .legacyProcessInstanceId(legacyProcessInstanceId)
        .variableKey(this.variableKey)
        .value(this.value)
        .name(this.name)
        .scopeKey(this.scopeKey)
        .processInstanceKey(this.processInstanceKey)
        .processDefinitionId(this.processDefinitionId)
        .tenantId(this.tenantId)).build();
  }

  public Long variableKey() {
    return variableKey;
  }

  public String name() {
    return name;
  }

  public ValueTypeEnum type() {
    return type;
  }

  public Double doubleValue() {
    return doubleValue;
  }

  public Long longValue() {
    return longValue;
  }

  public String value() {
    return value;
  }

  public String fullValue() {
    return fullValue;
  }

  public boolean isPreview() {
    return isPreview;
  }

  public Long scopeKey() {
    return scopeKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public String processDefinitionId() {
    return processDefinitionId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String legacyId() {
    return legacyId;
  }

  public String legacyProcessInstanceId() {
    return legacyProcessInstanceId;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (VariableDbModel) obj;
    return Objects.equals(this.variableKey, that.variableKey) && Objects.equals(this.name, that.name) && Objects.equals(
        this.type, that.type) && Objects.equals(this.doubleValue, that.doubleValue) && Objects.equals(this.longValue,
        that.longValue) && Objects.equals(this.value, that.value) && Objects.equals(this.fullValue, that.fullValue)
        && this.isPreview == that.isPreview && Objects.equals(this.scopeKey, that.scopeKey) && Objects.equals(
        this.processInstanceKey, that.processInstanceKey) && Objects.equals(this.processDefinitionId,
        that.processDefinitionId) && Objects.equals(this.tenantId, that.tenantId) && Objects.equals(this.legacyId,
        that.legacyId) && Objects.equals(this.legacyProcessInstanceId, that.legacyProcessInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variableKey, name, type, doubleValue, longValue, value, fullValue, isPreview, scopeKey,
        processInstanceKey, processDefinitionId, tenantId, legacyId, legacyProcessInstanceId);
  }

  @Override
  public String toString() {
    return "VariableDbModel[" + "variableKey=" + variableKey + ", " + "name=" + name + ", " + "type=" + type + ", "
        + "doubleValue=" + doubleValue + ", " + "longValue=" + longValue + ", " + "value=" + value + ", " + "fullValue="
        + fullValue + ", " + "isPreview=" + isPreview + ", " + "scopeKey=" + scopeKey + ", " + "processInstanceKey="
        + processInstanceKey + ", " + "processDefinitionId=" + processDefinitionId + ", " + "tenantId=" + tenantId
        + ", " + "legacyId=" + legacyId + ", " + "legacyProcessInstanceId=" + legacyProcessInstanceId + ']';
  }

  public static class VariableDbModelBuilder implements ObjectBuilder<VariableDbModel> {

    private Long variableKey;
    private String name;
    private String value;
    private Long scopeKey;
    private Long processInstanceKey;
    private String processDefinitionId;
    private String tenantId;
    private String legacyId;
    private String legacyProcessInstanceId;

    public VariableDbModelBuilder() {
    }

    public VariableDbModelBuilder legacyId(final String id) {
      legacyId = id;
      return this;
    }

    public VariableDbModelBuilder legacyProcessInstanceId(final String id) {
      legacyProcessInstanceId = id;
      return this;
    }

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
      return new VariableDbModel(variableKey, legacyId, legacyProcessInstanceId, processInstanceKey,
          processDefinitionId, scopeKey, ValueTypeEnum.STRING, name, null, null, value, null, tenantId, false);
    }

    private VariableDbModel getModelWithPreview() {
      return new VariableDbModel(variableKey, legacyId, legacyProcessInstanceId, processInstanceKey,
          processDefinitionId, scopeKey, ValueTypeEnum.STRING, name, null, null,
          value.substring(0, DEFAULT_VARIABLE_SIZE_THRESHOLD), value, tenantId, true);
    }

    private VariableDbModel getLongModel() {
      return new VariableDbModel(variableKey, legacyId, legacyProcessInstanceId, processInstanceKey,
          processDefinitionId, scopeKey, ValueTypeEnum.LONG, name, null, Long.parseLong(value), value, null, tenantId,
          false);
    }

    private VariableDbModel getDoubleModel() {
      return new VariableDbModel(variableKey, legacyId, legacyProcessInstanceId, processInstanceKey,
          processDefinitionId, scopeKey, ValueTypeEnum.DOUBLE, name, Double.parseDouble(value), null, value, null,
          tenantId, false);
    }
  }
}

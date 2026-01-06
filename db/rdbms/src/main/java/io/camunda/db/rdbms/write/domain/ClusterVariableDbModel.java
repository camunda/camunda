/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static io.camunda.util.ValueTypeUtil.mapBoolean;
import static io.camunda.util.ValueTypeUtil.mapDouble;
import static io.camunda.util.ValueTypeUtil.mapLong;

import io.camunda.db.rdbms.write.util.TruncateUtil;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.entities.ValueTypeEnum;
import io.camunda.util.ClusterVariableUtil;
import io.camunda.util.ObjectBuilder;
import io.camunda.util.ValueTypeUtil;
import java.util.function.Function;

public record ClusterVariableDbModel(
    String id,
    String name,
    ValueTypeEnum type,
    Double doubleValue,
    Long longValue,
    String value,
    String fullValue,
    boolean isPreview,
    String tenantId,
    ClusterVariableScope scope)
    implements Copyable<ClusterVariableDbModel> {

  @Override
  public ClusterVariableDbModel copy(
      final Function<ObjectBuilder<ClusterVariableDbModel>, ObjectBuilder<ClusterVariableDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new ClusterVariableDbModelBuilder()
                .name(name)
                .value(value)
                .tenantId(tenantId)
                .scope(scope))
        .build();
  }

  public ClusterVariableDbModel truncateValue(final int sizeLimit, final Integer byteLimit) {
    var truncatedValue = value;
    String fullValue = null;
    var isPreview = false;

    if (type == ValueTypeEnum.STRING && value != null) {
      truncatedValue = TruncateUtil.truncateValue(truncatedValue, sizeLimit, byteLimit);

      if (truncatedValue.length() < value.length()) {
        fullValue = value;
        isPreview = true;
      }
    }

    return new ClusterVariableDbModel(
        id,
        name,
        type,
        doubleValue,
        longValue,
        truncatedValue,
        fullValue,
        isPreview,
        tenantId,
        scope);
  }

  public static class ClusterVariableDbModelBuilder
      implements ObjectBuilder<ClusterVariableDbModel> {
    private String name;
    private String value;
    private String tenantId;
    private ClusterVariableScope scope;

    public ClusterVariableDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public ClusterVariableDbModelBuilder value(final String value) {
      this.value = value;
      return this;
    }

    public ClusterVariableDbModelBuilder scope(final ClusterVariableScope scope) {
      this.scope = scope;
      return this;
    }

    public ClusterVariableDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public ClusterVariableDbModel build() {
      return switch (ValueTypeUtil.getValueType(value)) {
        case LONG -> getLongModel();
        case DOUBLE -> getDoubleModel();
        case BOOLEAN -> getModel(ValueTypeEnum.BOOLEAN, mapBoolean(value));
        case NULL -> getModel(ValueTypeEnum.NULL, null);
        default -> getModel(ValueTypeEnum.STRING, value);
      };
    }

    private ClusterVariableDbModel getLongModel() {
      return new ClusterVariableDbModel(
          getCompositeId(),
          name,
          ValueTypeEnum.LONG,
          null,
          mapLong(value),
          value,
          null,
          false,
          tenantId,
          scope);
    }

    private String getCompositeId() {
      return ClusterVariableUtil.generateID(name, tenantId, scope);
    }

    private ClusterVariableDbModel getModel(final ValueTypeEnum valueTypeEnum, final String value) {
      return new ClusterVariableDbModel(
          getCompositeId(), name, valueTypeEnum, null, null, value, null, false, tenantId, scope);
    }

    private ClusterVariableDbModel getDoubleModel() {
      return new ClusterVariableDbModel(
          getCompositeId(),
          name,
          ValueTypeEnum.DOUBLE,
          mapDouble(value),
          null,
          value,
          null,
          false,
          tenantId,
          scope);
    }
  }
}

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

import io.camunda.search.entities.ValueTypeEnum;
import io.camunda.util.ObjectBuilder;
import io.camunda.util.ValueTypeUtil;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
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
    String tenantId,
    int partitionId,
    OffsetDateTime historyCleanupDate)
    implements Copyable<VariableDbModel> {

  @Override
  public VariableDbModel copy(
      final Function<ObjectBuilder<VariableDbModel>, ObjectBuilder<VariableDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new VariableDbModelBuilder()
                .variableKey(variableKey)
                .value(value)
                .name(name)
                .scopeKey(scopeKey)
                .processInstanceKey(processInstanceKey)
                .processDefinitionId(processDefinitionId)
                .tenantId(tenantId)
                .partitionId(partitionId)
                .historyCleanupDate(historyCleanupDate))
        .build();
  }

  /**
   * Truncates the value of the variable if it exceeds the specified size limit or byte limit.
   * Truncate is done in two steps: First based on the character size limit, and then based on the
   * (optional) byte size limit, if the byte size exceeds the specified limit.
   *
   * @param sizeLimit the maximum number of characters allowed for the variable value
   * @param byteLimit the maximum number of bytes allowed for the variable value
   * @return a new VariableDbModel with the truncated value
   */
  public VariableDbModel truncateValue(final int sizeLimit, final Integer byteLimit) {
    var truncatedValue = value;
    String fullValue = null;
    var isPreview = false;

    if (type == ValueTypeEnum.STRING && value != null) {
      if (truncatedValue.length() > sizeLimit) {
        truncatedValue = truncatedValue.substring(0, sizeLimit);
        fullValue = value;
        isPreview = true;
      }

      if ((byteLimit != null
          && truncatedValue.getBytes(StandardCharsets.UTF_8).length > byteLimit)) {
        truncatedValue = truncateBytes(truncatedValue, byteLimit);
        fullValue = value;
        isPreview = true;
      }
    }

    return new VariableDbModel(
        variableKey,
        name,
        type,
        doubleValue,
        longValue,
        truncatedValue,
        fullValue,
        isPreview,
        scopeKey,
        processInstanceKey,
        processDefinitionId,
        tenantId,
        partitionId,
        historyCleanupDate);
  }

  /**
   * Truncates the given string to ensure that its byte representation does not exceed the specified
   * maximum byte size. This is necessary for DB vendors, which have a limit on the byte size for
   * char columns. Currently, this is needed for Oracle's varchar2 only.
   *
   * @param original the original string to truncate
   * @param maxBytes the maximum allowed byte size
   * @return the truncated string, or an empty string if it cannot be truncated to fit within the
   *     limit
   */
  private String truncateBytes(final String original, final int maxBytes) {
    final byte[] bytes = original.getBytes(StandardCharsets.UTF_8);

    if (bytes.length <= maxBytes) {
      return original;
    }

    var truncatedVariable = original;

    while (truncatedVariable.getBytes().length > maxBytes) {
      truncatedVariable = truncatedVariable.substring(0, truncatedVariable.length() - 1);

      if (truncatedVariable.getBytes().length <= maxBytes) {
        return truncatedVariable;
      }
    }
    return "";
  }

  public static class VariableDbModelBuilder implements ObjectBuilder<VariableDbModel> {

    private Long variableKey;
    private String name;
    private String value;
    private Long scopeKey;
    private Long processInstanceKey;
    private String processDefinitionId;
    private String tenantId;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

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

    public VariableDbModelBuilder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public VariableDbModelBuilder historyCleanupDate(final OffsetDateTime value) {
      historyCleanupDate = value;
      return this;
    }

    // Build method to create the record
    @Override
    public VariableDbModel build() {
      return switch (ValueTypeUtil.getValueType(value)) {
        case LONG -> getLongModel();
        case DOUBLE -> getDoubleModel();
        case BOOLEAN -> getModel(ValueTypeEnum.BOOLEAN, mapBoolean(value));
        case NULL -> getModel(ValueTypeEnum.NULL, null);
        default -> getModel(ValueTypeEnum.STRING, value);
      };
    }

    private VariableDbModel getModel(final ValueTypeEnum valueTypeEnum, final String value) {
      return new VariableDbModel(
          variableKey,
          name,
          valueTypeEnum,
          null,
          null,
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId,
          partitionId,
          historyCleanupDate);
    }

    private VariableDbModel getLongModel() {
      return new VariableDbModel(
          variableKey,
          name,
          ValueTypeEnum.LONG,
          null,
          mapLong(value),
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId,
          partitionId,
          historyCleanupDate);
    }

    private VariableDbModel getDoubleModel() {
      return new VariableDbModel(
          variableKey,
          name,
          ValueTypeEnum.DOUBLE,
          mapDouble(value),
          null,
          value,
          null,
          false,
          scopeKey,
          processInstanceKey,
          processDefinitionId,
          tenantId,
          partitionId,
          historyCleanupDate);
    }
  }
}

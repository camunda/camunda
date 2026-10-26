/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.db.rdbms.write.util.TruncateUtil;
import java.time.OffsetDateTime;

/** PoC: one variable change with its value, as stored in the VARIABLE_HISTORY table. */
public record VariableHistoryDbModel(
    String variableHistoryKey,
    long variableKey,
    String name,
    long scopeKey,
    long processInstanceKey,
    Long rootProcessInstanceKey,
    long processDefinitionKey,
    String processDefinitionId,
    String tenantId,
    int partitionId,
    long position,
    OffsetDateTime eventTime,
    String operation,
    String actorType,
    String actorId,
    int valueSize,
    boolean isPreview,
    String value,
    String fullValue) {

  /** Cuts the value to a preview and keeps the full value when it is longer than the preview. */
  public VariableHistoryDbModel truncateValue(final int sizeLimit, final Integer byteLimit) {
    final var preview = TruncateUtil.truncateValue(value, sizeLimit, byteLimit);
    final var truncated = preview != null && preview.length() < value.length();
    return new VariableHistoryDbModel(
        variableHistoryKey,
        variableKey,
        name,
        scopeKey,
        processInstanceKey,
        rootProcessInstanceKey,
        processDefinitionKey,
        processDefinitionId,
        tenantId,
        partitionId,
        position,
        eventTime,
        operation,
        actorType,
        actorId,
        valueSize,
        truncated,
        preview,
        truncated ? value : null);
  }
}

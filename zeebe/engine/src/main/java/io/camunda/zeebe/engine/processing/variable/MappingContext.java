/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Per-activation context passed to {@link MappingResolver} so implementations that need element
 * identity (e.g. {@link ComparingMappingResolver}) can log meaningful diagnostics without receiving
 * the information at construction time.
 *
 * <p>{@code elementId} is kept as a {@link DirectBuffer} to avoid a String allocation on every
 * activation; the conversion happens only when this context is included in a log message.
 */
@NullMarked
public record MappingContext(
    DirectBuffer elementId,
    long scopeKey,
    long processInstanceKey,
    long processDefinitionKey,
    String tenantId) {

  @Override
  public String toString() {
    return "MappingContext[elementId="
        + BufferUtil.bufferAsString(elementId)
        + ", scopeKey="
        + scopeKey
        + ", processInstanceKey="
        + processInstanceKey
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", tenantId="
        + tenantId
        + "]";
  }
}

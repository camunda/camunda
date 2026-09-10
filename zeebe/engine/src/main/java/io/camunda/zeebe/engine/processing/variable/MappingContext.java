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
 *
 * @param scopeKey the element's own scope, used to evaluate mapping source expressions
 * @param mergeTargetScopeKey the scope the resolved result will be merged into: the element's own
 *     scope for an input mapping, or the flow-scope key for an output mapping (unless the element
 *     is an inner multi-instance activity, where it is again the element's own scope). Only {@code
 *     OrderedOutputMappingResolver} reads this field, to seed a nested output target's merge from
 *     the value already in that target scope instead of from the completing element's own,
 *     about-to-be-discarded scope — see <a
 *     href="https://github.com/camunda/camunda/issues/35251">#35251</a>. Every other resolver
 *     ({@code CombinedOutputMappingResolver} and both input-mapping resolvers) ignores it.
 */
@NullMarked
public record MappingContext(
    DirectBuffer elementId,
    long scopeKey,
    long processInstanceKey,
    long processDefinitionKey,
    String tenantId,
    long mergeTargetScopeKey) {

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
        + ", mergeTargetScopeKey="
        + mergeTargetScopeKey
        + "]";
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

/**
 * The base-projection (read-model) entry for a single process instance, keyed by {@code
 * processInstanceKey}. Built by folding the consumed record stream: the root process element's
 * {@code ELEMENT_ACTIVATED} sets {@code startTime}; {@code ELEMENT_COMPLETED}/{@code
 * ELEMENT_TERMINATED} sets {@code endTime}.
 *
 * <p>{@code UNSET} marks a not-yet-observed timestamp. {@code factEmitted} guards against
 * re-deriving the execution-time fact for an instance that has already produced one.
 */
public record ProcessInstanceProjection(
    long processInstanceKey,
    long processDefinitionKey,
    String bpmnProcessId,
    int version,
    String tenantId,
    long startTime,
    long endTime,
    boolean terminated,
    boolean factEmitted) {

  public static final long UNSET = -1L;

  public boolean hasStart() {
    return startTime != UNSET;
  }

  public boolean hasEnd() {
    return endTime != UNSET;
  }

  /** True once both ends are observed and the execution time can be derived. */
  public boolean isComplete() {
    return hasStart() && hasEnd();
  }
}

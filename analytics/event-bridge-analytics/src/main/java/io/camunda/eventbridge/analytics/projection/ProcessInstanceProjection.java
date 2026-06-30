/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import java.util.Map;

/**
 * The base-projection (read-model) entry for a single process instance, keyed by {@code
 * processInstanceKey}. Built by folding the consumed record stream: the root process element's
 * {@code ELEMENT_ACTIVATED} sets {@code startTime}; {@code ELEMENT_COMPLETED}/{@code
 * ELEMENT_TERMINATED} sets {@code endTime}; {@code VARIABLE} records accumulate the instance's
 * {@code variables} (last write wins) so a derived fact can be enriched with them (e.g. to group by
 * a {@code region} variable). All variables are kept for simplicity.
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
    boolean factEmitted,
    Map<String, String> variables) {

  public static final long UNSET = -1L;

  /** A projection seeded only by a variable (before the root element has been observed). */
  public static ProcessInstanceProjection withVariablesOnly(
      final long processInstanceKey, final Map<String, String> variables) {
    return new ProcessInstanceProjection(
        processInstanceKey, -1L, "", -1, "", UNSET, UNSET, false, false, variables);
  }

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

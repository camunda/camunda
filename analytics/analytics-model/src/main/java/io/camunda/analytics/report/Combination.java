/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.report;

/**
 * How a {@link ReportDefinition}'s dataset sources are combined into one result.
 *
 * <ul>
 *   <li>{@link #UNION} — stack the sources' rows on the shared {@code (group-by, bucket)} key;
 *       supported today. Sources should share the group-by grain; each source's measures are
 *       namespaced by its dataset so same-named meters never collide. See ADR 0006.
 *   <li>{@link #JOIN} — align sources of differing grains on a shared dimension; reserved, not yet
 *       implemented (needs an app-side join executor).
 * </ul>
 */
public enum Combination {
  UNION,
  JOIN
}

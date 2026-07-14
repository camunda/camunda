/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.fact;

/**
 * The lifecycle transition a {@link Fact} records. Carried as the reserved {@link Fact#TRANSITION}
 * field (by name, so it can be a group-by dimension and a filter target — e.g. a per-meter {@code
 * transition = COMPLETED} count).
 *
 * <p><b>Name-mirroring convention.</b> Constants mirror, by name, the upstream record intents they
 * are derived from (the {@code ELEMENT_ACTIVATED/COMPLETED/TERMINATED} family folds to {@code
 * ACTIVATED}/{@code COMPLETED}/{@code TERMINATED}, incident {@code CREATED}/{@code RESOLVED} stay
 * {@code CREATED}/{@code RESOLVED}), deviating only where the mirrored name would be ambiguous
 * across fact types (process-definition {@code CREATED} becomes {@code DEPLOYED} so it cannot be
 * confused with an incident's {@code CREATED}). This enum is the anti-corruption layer between
 * exporter records and facts: only the fact derivers map upstream intents onto it, and everything
 * downstream — declarations, filters, dimensions, serving rows — speaks these names, never raw
 * intent names. Renaming a constant is a breaking change to persisted declarations and stored
 * dimension values; when upstream adds an intent worth modelling, add a constant here following the
 * convention rather than leaking the record's own vocabulary into facts.
 */
public enum Transition {
  ACTIVATED,
  COMPLETED,
  TERMINATED,
  CREATED,
  RESOLVED,
  DEPLOYED
}

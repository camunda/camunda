/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import io.camunda.analytics.lake.sink.algebra.Algebra;
import java.util.List;

/**
 * One {@code .measure(name, algebras...)} declaration, resolved against the raw schema.
 *
 * @param name the measure name, e.g. {@code work_time_ms}
 * @param rawColumnIndex the measure's column index in the raw schema
 * @param algebras every algebra folding this measure's raw values into partial state (e.g. both
 *     scalar stats and a histogram for the same raw column); declaration order is preserved
 */
record MeasureDeclaration(String name, int rawColumnIndex, List<Algebra> algebras) {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
/**
 * Mergeable metric algebras: pure library code turning raw {@code long} values into partial state
 * that can be merged with any other partial state of the same algebra, forever, without access to
 * the original values — see {@link io.camunda.analytics.lake.sink.algebra.Algebra}'s own javadoc
 * for the full contract.
 *
 * <p>This package has no dependency on, and is not wired into, any pipeline in this codebase (see
 * {@link io.camunda.analytics.lake.sink.algebra.Algebra.RowWriter}'s javadoc for why): it is
 * consumed by {@link io.camunda.analytics.lake.metrics}, which compiles a declared set of
 * dimensions/measures/algebras into the physical partials-table schemas and generated SQL, but a
 * later milestone is responsible for actually folding raw records through it on a flush thread.
 */
package io.camunda.analytics.lake.sink.algebra;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * The read-facing result of {@link DistinctCountAggregateFunction}: the estimated number of
 * distinct values, with a confidence interval ({@code lowerBound}..{@code upperBound}) around the
 * estimate.
 *
 * @param estimate the estimated distinct-value count
 * @param lowerBound the lower confidence bound on the estimate
 * @param upperBound the upper confidence bound on the estimate
 */
public record DistinctCountResult(long estimate, long lowerBound, long upperBound) {}

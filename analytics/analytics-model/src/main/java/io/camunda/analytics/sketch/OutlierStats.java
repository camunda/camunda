/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

/**
 * The boxplot outlier statistics of a duration (or other numeric) distribution, computed from a
 * {@link QuantileResult}'s merged sketch by {@link QuantileResult#outlierStats()} — the same rule
 * Camunda Optimize uses: {@code fence = Q3 + 1.5 * (Q3 - Q1)}, and everything above the fence is an
 * outlier. All figures beyond {@code n} are KLL approximations.
 *
 * @param n the observation count backing this distribution
 * @param median the estimated median (rank 0.5)
 * @param q1 the estimated first quartile (rank 0.25)
 * @param q3 the estimated third quartile (rank 0.75)
 * @param fence the outlier fence, {@code q3 + 1.5 * (q3 - q1)}
 * @param share the estimated fraction of observations above the fence, {@code 1 - rank(fence)}
 * @param count the estimated number of observations above the fence, {@code round(share * n)}
 */
public record OutlierStats(
    long n, double median, double q1, double q3, double fence, double share, long count) {}

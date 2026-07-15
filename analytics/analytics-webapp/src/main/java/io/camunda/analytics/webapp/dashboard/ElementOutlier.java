/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One flow node's duration outliers over the range: the boxplot fence ({@code Q3 + 1.5 * IQR})
 * computed from the elements cube's completion-duration sketch, and how many/what share of its
 * completions sit above it. Approximate (KLL-backed) — every duration here is a sketch estimate,
 * not an exact figure.
 *
 * @param elementId the flow node
 * @param n the number of completions backing the distribution
 * @param medianMs the estimated median completion duration
 * @param q3Ms the estimated third quartile
 * @param fenceMs the outlier fence, {@code q3Ms + 1.5 * (q3Ms - q1Ms)}
 * @param share the estimated fraction of completions above the fence
 * @param count the estimated number of completions above the fence
 */
public record ElementOutlier(
    String elementId, long n, long medianMs, long q3Ms, long fenceMs, double share, long count) {}

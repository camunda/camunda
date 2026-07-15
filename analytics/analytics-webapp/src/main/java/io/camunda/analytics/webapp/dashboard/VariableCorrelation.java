/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * How over-represented one variable value is among duration outliers over the range: {@code share}
 * is the estimated fraction of this value's completions above the process's overall outlier fence,
 * and {@code lift = share / overallShare} — a value with lift &gt;&gt; 1 is disproportionately
 * likely to be an outlier. Declared-variable cubes only ({@code corr-*}); approximate (KLL-backed).
 *
 * @param variable the variable name, without its {@code var.} dimension prefix
 * @param value the observed value of the variable
 * @param n the number of completions carrying this value
 * @param share the estimated fraction of this value's completions above the overall fence
 * @param lift {@code share / overallShare}
 */
public record VariableCorrelation(
    String variable, String value, long n, double share, double lift) {}

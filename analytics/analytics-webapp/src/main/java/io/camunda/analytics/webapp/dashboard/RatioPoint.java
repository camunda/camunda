/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One window's percentage/ratio for a process (SLA-met or no-incident): matched of total, and the
 * derived fraction in [0, 1]. Counts are additive so they merge exactly across windows. {@code
 * maturing} is true for a not-yet-final SLA start cohort (younger than the SLA target), whose
 * matched count may still rise; for such a point {@code ratio} is the lower bound (no more meet)
 * and {@code ratioUpper} the upper bound (every still-open instance meets). For settled points and
 * the completion-based no-incident metric, {@code ratioUpper == ratio} and {@code maturing} is
 * false.
 */
public record RatioPoint(
    long windowStart,
    long matched,
    long total,
    double ratio,
    double ratioUpper,
    boolean maturing) {}

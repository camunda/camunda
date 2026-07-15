/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One window of the completion-duration spread: the average with its population standard deviation
 * (the control-chart band) and the exact extrema of the durations completed in the window (the
 * {@code process-duration-spread} cube's execution-time + stddev primitives).
 */
public record DurationSpreadPoint(
    long windowStart, double avgMs, long minMs, long maxMs, double stddevMs) {}

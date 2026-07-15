/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One window of the flow-balance (Little's law) series: instances that {@code started} (activated)
 * and {@code ended} (completed plus terminated) in it. Rendered against the active-instances
 * snapshot series — arrivals vs completions vs WIP in one glance.
 */
public record LifecycleSeriesPoint(long windowStart, long started, long ended) {}

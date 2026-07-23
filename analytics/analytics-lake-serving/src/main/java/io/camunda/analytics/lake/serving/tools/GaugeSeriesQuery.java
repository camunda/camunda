/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

/**
 * {@code POST /api/tools/gauge-series} request: the WIP-over-time tile's own periodic gauge read,
 * separate from {@link SeriesQuery} because {@code open_instances_gauge} is a plain sample table
 * with no metrics-registry entity behind it (see {@link GaugeSeriesService}).
 *
 * @param processId {@code null} means "every process, summed per sample instant" (see {@link
 *     GaugeSeriesService})
 * @param from ISO-8601 inclusive start (or an epoch-milliseconds digit string -- see {@code
 *     SqlText#parseInstant})
 * @param to ISO-8601 exclusive end
 * @param grainMinutes the output bucket width, in minutes
 */
public record GaugeSeriesQuery(String processId, String from, String to, Integer grainMinutes) {}

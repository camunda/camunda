/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.examples.flink;

import java.io.Serializable;

/**
 * The serving-store cell: one row of the materialized result. This is what the sink writes.
 *
 * <p>Grain: (processId, 1-minute event-time window). The metric is {@code count} plus
 * {@code avgDurationMs} over the completed instances that fell in that window. This mirrors the
 * "serving cell" in our design — the smallest independently-addressable unit the dashboard reads.
 *
 * @param processId the grouping key
 * @param windowStartMs start of the tumbling event-time window, in epoch millis
 * @param count number of completed instances in the cell
 * @param avgDurationMs mean duration in millis over those instances
 */
public record Cell(String processId, long windowStartMs, long count, double avgDurationMs)
    implements Serializable {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.kafkastreams;

/**
 * The serving record produced by Stage C — one cell of the analytics cube.
 *
 * <p>Grain: (processId, 1-minute window). This is what would be written to the serving store; here
 * the sink just logs it.
 *
 * @param processId grouping key
 * @param windowStartMs start of the 1-minute tumbling event-time window
 * @param count number of completion facts in the window
 * @param avgDurationMs mean duration across those facts
 */
public record Cell(String processId, long windowStartMs, long count, double avgDurationMs) {}

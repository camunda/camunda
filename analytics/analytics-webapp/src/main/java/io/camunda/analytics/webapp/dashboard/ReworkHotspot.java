/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One flow node's rework estimate: {@code activations} counted exactly, {@code instances} as the
 * HLL distinct estimate over process-instance keys, and {@code rework = max(0, activations −
 * instances)} — exact while the sketch is exact (small counts), an approximation at scale.
 */
public record ReworkHotspot(String elementId, long activations, long instances, long rework) {}

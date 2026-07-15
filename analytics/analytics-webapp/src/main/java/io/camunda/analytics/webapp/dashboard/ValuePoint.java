/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One sample of the value-in-flight series: the summed business value of the instances running at
 * {@code time} (the value-in-flight cube's periodic snapshots, carried forward like the
 * active-instances series). Plain numbers, no currency — the value variable is unitless.
 */
public record ValuePoint(long time, long value) {}

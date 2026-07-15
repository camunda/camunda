/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * The business value processed over a range: the sum of the designated value variable across the
 * COMPLETED instances (the value-throughput cube). {@code processed} is {@code null} when the
 * process never carried the variable in range — "no value data", which the tile renders as a dash,
 * is different from a genuine total of 0.
 */
public record ValueSummary(Long processed) {}

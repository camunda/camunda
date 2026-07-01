/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One window's distinct-process estimate for a tenant (HLL), with the confidence interval around
 * the estimate.
 */
public record DistinctPoint(long windowStart, long estimate, long lowerBound, long upperBound) {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One window of the incident trend: how many incidents were {@code raised} (CREATED facts) in it,
 * summed across the process's flow nodes. The quality page renders this beside the per-node
 * incidents table to show whether incidents are a burst or a steady leak.
 */
public record IncidentTrendPoint(long windowStart, long raised) {}

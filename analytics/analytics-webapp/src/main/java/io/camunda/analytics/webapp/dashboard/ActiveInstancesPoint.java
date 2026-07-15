/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One point of the active-instances series: how many instances of the process were running at
 * {@code time} — an absolute value carried forward from the cube's periodic snapshots (ADR 0010),
 * not a per-window flow.
 */
public record ActiveInstancesPoint(long time, long active) {}

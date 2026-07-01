/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One start cohort of the forward-looking SLA metric: the instances that <em>started</em> in this
 * window, split by outcome. {@code started = met + breached + open}. {@code met} completed within
 * the target, {@code breached} completed but missed it (or were terminated), {@code open} are still
 * running (undecided — only present while {@code maturing}). A read renders these as a stacked bar
 * (height = started); the {@code maturing} window is drawn distinctly because its split can still
 * change as its open instances finish.
 */
public record SlaCohortPoint(
    long windowStart, long started, long met, long breached, long open, boolean maturing) {}

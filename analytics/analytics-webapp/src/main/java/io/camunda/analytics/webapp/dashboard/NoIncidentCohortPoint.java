/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.webapp.dashboard;

/**
 * One start cohort of the no-incident metric: the instances that <em>started</em> in this window,
 * split by outcome. {@code started = clean + withIncident + open}. {@code clean} completed without
 * raising an incident, {@code withIncident} completed having raised one (or were terminated),
 * {@code open} are still running (undecided — only present while {@code maturing}). Mirrors {@link
 * SlaCohortPoint}: {@code started} is the true cohort size (from the lifecycle summary), not just
 * its settled, completion-based part.
 */
public record NoIncidentCohortPoint(
    long windowStart, long started, long clean, long withIncident, long open, boolean maturing) {}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

/**
 * When a variable dimension's value is snapshotted onto a fact (the deck's "Enriching Facts with
 * Variable Dimensions"). Each timing maps to a fixed source position, so the stamped value is a
 * pure function of the log up to that point — replay-deterministic. A dataset declaration chooses
 * the timing per variable dimension; the projector resolves it via {@link VariableEnricher}.
 */
public enum EnrichmentTiming {

  /** The value as it stands when the measured event is folded (immediate; may change later). */
  EVENT_TIME,

  /** The value as of the process instance's activation (stable across the instance's events). */
  PI_CREATE,

  /** The value as of the process instance's completion (final; only known once it completes). */
  PI_COMPLETE
}

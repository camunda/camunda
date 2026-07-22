/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

/**
 * Why a segment was sealed. Purely informational (metrics, file-boundary decisions): seal timing is
 * a performance knob and must never affect committed content.
 */
public enum SealReason {
  /** The segment reached its row capacity — the continuous trickle trigger. */
  SEGMENT_FULL,
  /** The flush interval elapsed — finalize the current file after this segment. */
  TIME_DUE,
  /** The current file reached its target size — finalize after this segment. */
  SIZE_CAP,
  /** Orderly shutdown — drain everything, finalize, emit descriptors. */
  SHUTDOWN
}

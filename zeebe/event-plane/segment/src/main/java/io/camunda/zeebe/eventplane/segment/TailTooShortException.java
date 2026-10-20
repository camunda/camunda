/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

/**
 * A tail that starts after the metadata, which is longer than usual. The reader reads the object
 * again from {@link #metadataStart()}.
 */
public final class TailTooShortException extends Exception {

  private final long metadataStart;

  public TailTooShortException(long tailStart, long metadataStart) {
    super(
        "Expected the tail to start at or before the metadata at byte %d, but it starts at %d"
            .formatted(metadataStart, tailStart));
    this.metadataStart = metadataStart;
  }

  /** Where the metadata starts in the object: the footer's body length. */
  public long metadataStart() {
    return metadataStart;
  }
}

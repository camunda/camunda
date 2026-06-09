/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

/**
 * Thrown during an in-transaction flush when the exporter position stored in the database is
 * <em>more advanced</em> than the local {@code lastFlushedPosition}.
 *
 * <p>This typically indicates that a concurrent exporter instance for the same partition has
 * already written records beyond the current instance's last acknowledged position.
 */
public final class ExporterPositionMismatchException extends RuntimeException {

  private final long expectedPosition;
  private final long actualPosition;

  public ExporterPositionMismatchException(
      final int partitionId, final long expectedPosition, final long actualPosition) {
    super(
        String.format(
            "Exporter position for partition %d is ahead of the local position:"
                + " expected %d but found %d in the database."
                + " Another exporter instance may have already exported to this partition.",
            partitionId, expectedPosition, actualPosition));

    this.expectedPosition = expectedPosition;
    this.actualPosition = actualPosition;
  }

  public boolean isBehind() {
    return expectedPosition < actualPosition;
  }

  public boolean isAhead() {
    return expectedPosition > actualPosition;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.exception;

/**
 * Thrown during an in-transaction flush when the exporter position stored in the database differs
 * from the local expected position.
 *
 * <p>This can indicate either a concurrent exporter instance writing ahead (DB position > expected)
 * or an operator/DB rollback which moved the position backwards (DB position < expected).
 */
public final class ExporterPositionMismatchException extends RuntimeException {

  public ExporterPositionMismatchException(
      final int partitionId, final long expectedPosition, final long actualPosition) {
    super(
        String.format(
            "Exporter position mismatch for partition %d: expected %d but found %d in the database.",
            partitionId, expectedPosition, actualPosition));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

/**
 * Thrown when the exporter position stored in the database does not match the locally expected
 * position. This indicates that another exporter instance may have concurrently written to the same
 * partition, causing the positions to diverge.
 *
 * <p>Recovery requires closing and reopening the exporter so that it can re-read the current
 * position from the database and reset all volatile in-memory state.
 */
public final class ExporterPositionMismatchException extends RuntimeException {

  public ExporterPositionMismatchException(final String message) {
    super(message);
  }
}

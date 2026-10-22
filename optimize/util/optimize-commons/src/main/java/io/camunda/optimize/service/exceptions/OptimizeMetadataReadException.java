/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

/**
 * Thrown when the Optimize metadata document cannot be read from the database, for example because
 * the database is unreachable or the metadata index shard is not available yet. A schema version
 * mismatch is not a read failure and is reported with a plain {@link OptimizeRuntimeException}.
 */
public class OptimizeMetadataReadException extends OptimizeRuntimeException {

  public OptimizeMetadataReadException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

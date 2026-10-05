/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

/**
 * Used when the number of documents processed by a reindex or delete operation does not match the
 * expected count. This is caught in the future chain to end the current execution gracefully so the
 * same batch can be retried on the next invocation.
 */
class BatchCountMismatchException extends RuntimeException {
  final String operation;

  BatchCountMismatchException(final String operation, final String message) {
    super(message);
    this.operation = operation;
  }
}

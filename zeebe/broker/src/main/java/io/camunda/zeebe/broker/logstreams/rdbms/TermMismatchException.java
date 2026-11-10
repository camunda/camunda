/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.logstreams.rdbms;

/** The partition is not, or no longer, held by the term that tried to append to it. */
public final class TermMismatchException extends LogBlockRepositoryException {

  public TermMismatchException(final int partitionId, final long term) {
    super("Partition %d is not held by term %d".formatted(partitionId, term));
  }
}

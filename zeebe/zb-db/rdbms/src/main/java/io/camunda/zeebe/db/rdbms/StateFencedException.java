/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

/**
 * Thrown when a leader of a newer term owns the partition state: the state can't be claimed, and
 * writes of the older term are rejected. Trying again never helps, so unlike {@link
 * io.camunda.zeebe.db.ZeebeDbException} this is not meant to be retried.
 */
public final class StateFencedException extends RuntimeException {

  public StateFencedException(final String message) {
    super(message);
  }
}

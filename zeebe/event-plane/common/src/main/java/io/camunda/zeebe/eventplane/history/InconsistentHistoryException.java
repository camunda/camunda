/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import java.util.UUID;

/**
 * Manifests of histories that contradict each other: two of one history that differ, parts that
 * disagree, histories of different tenants, or a checkpoint that lies before its source's own
 * entries. Repeating the request won't help.
 */
public final class InconsistentHistoryException extends IllegalArgumentException {

  private final UUID history;

  public InconsistentHistoryException(final UUID history, final String message) {
    super(message);
    this.history = history;
  }

  /** The history whose manifest is at odds with another. */
  public UUID history() {
    return history;
  }
}

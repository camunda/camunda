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
 * The manifest of a history that a lineage needs isn't there, yet. It may still arrive, so a later
 * request may succeed.
 */
public final class MissingManifestException extends IllegalStateException {

  private final UUID history;

  public MissingManifestException(final UUID history) {
    super("Expected a manifest of history %s, but got none yet".formatted(history));
    this.history = history;
  }

  /** The history whose manifest is missing. */
  public UUID history() {
    return history;
  }
}

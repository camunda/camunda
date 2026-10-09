/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import java.util.OptionalLong;

/** Parses checkpoint ids embedded in backup store object keys and file paths. */
public final class CheckpointIds {

  private CheckpointIds() {}

  /**
   * Parses a checkpoint id matched by a store's own {@code \d+} pattern. That pattern accepts a
   * digit run of any length, including one too long for a {@code long}, so this never throws:
   * returns empty instead of letting one foreign or corrupted object (a crash leftover, external
   * tooling) fail listing, retention or the in-progress scan for the whole partition.
   */
  public static OptionalLong tryParse(final String digits) {
    try {
      return OptionalLong.of(Long.parseLong(digits));
    } catch (final NumberFormatException e) {
      return OptionalLong.empty();
    }
  }
}

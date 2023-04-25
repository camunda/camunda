/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.raft;

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.zeebe.EntryValidator;

public final class ZeebeEntryValidator implements EntryValidator {
  @Override
  public ValidationResult validateEntry(
      final ApplicationEntry lastEntry, final ApplicationEntry entry) {
    if (lastEntry == null) {
      return ValidationResult.ok();
    }

//    if (entry.lowestPosition() != lastEntry.highestPosition() + 1) {
//      return ValidationResult.failure(
//          String.format(
//              "Expected no gaps between application entries, but the last application entry had a "
//                  + "highest position of %d and the current entry has a lowest position of %d",
//              lastEntry.highestPosition(), entry.lowestPosition()));
//    }

    return ValidationResult.ok();
  }
}

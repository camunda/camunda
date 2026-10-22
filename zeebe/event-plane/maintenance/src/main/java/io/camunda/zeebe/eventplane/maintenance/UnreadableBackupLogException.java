/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import java.io.IOException;

/** A backup's log in the store isn't valid, so what the backup needs of the log is unknown. */
final class UnreadableBackupLogException extends Exception {

  private final String key;

  UnreadableBackupLogException(final String key, final IOException cause) {
    super(
        "Expected the backup log '%s' to be a valid backup log, but got: %s"
            .formatted(key, cause.getMessage()),
        cause);
    this.key = key;
  }

  /** The key of the backup's log. */
  String key() {
    return key;
  }
}

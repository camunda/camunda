/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal;

import io.camunda.zeebe.util.exception.UnrecoverableException;

/**
 * Represents a failure to read or write to the journal which resulted in an inconsistency. This is
 * an unrecoverable error which most likely requires manual intervention to fix (if possible).
 */
public final class CorruptedJournalException extends UnrecoverableException {

  public CorruptedJournalException(final String message) {
    super(message);
  }

  public CorruptedJournalException(final Throwable cause) {
    super(cause);
  }

  public CorruptedJournalException(final String message, final Throwable cause) {
    super(message, cause);
  }
}

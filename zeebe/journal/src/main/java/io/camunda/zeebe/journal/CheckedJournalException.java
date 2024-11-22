/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal;

import java.io.IOException;

public sealed class CheckedJournalException extends Exception {

  public CheckedJournalException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public static final class FlushException extends CheckedJournalException {
    public FlushException(final IOException cause) {
      super("Error when flushing", cause);
    }
  }
}

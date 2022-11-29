/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.log;

import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;

@FunctionalInterface
public interface LogStreamWriter {

  /**
   * Attempts to write the event to the underlying stream.
   *
   * @param appendEntry the entry to write
   * @return the event position, a negative value if fails to write the event, or 0 if the value is
   *     empty
   */
  default long tryWrite(final LogAppendEntry appendEntry) {
    return tryWrite(appendEntry, LogEntryDescriptor.KEY_NULL_VALUE);
  }

  /**
   * Attempts to write the event to the underlying stream.
   *
   * @param appendEntry the entry to write
   * @param sourcePosition a back-pointer to the record whose processing created this entry
   * @return the event position, a negative value if fails to write the event, or 0 if the value is
   *     empty
   */
  long tryWrite(final LogAppendEntry appendEntry, final long sourcePosition);
}

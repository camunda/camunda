/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.logstreams.log.LoggedEvent;
import java.util.Objects;

/** Implement to control which events should be handled by a {@link StreamProcessor}. */
@FunctionalInterface
public interface EventFilter {

  /**
   * @param event the event to be processed next
   * @return true to mark an event for processing; false to skip it
   * @throws RuntimeException to signal that processing cannot continue
   */
  boolean applies(LoggedEvent event);

  default EventFilter and(final EventFilter other) {
    Objects.requireNonNull(other);
    return (e) -> applies(e) && other.applies(e);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.protocol.impl.record.RecordMetadata;
import java.util.Objects;

/**
 * Implement to control which events should be handled by a {@link StreamProcessor} based on the
 * event's metadata.
 */
@FunctionalInterface
public interface MetadataFilter {
  /**
   * @param metadata the metadata of the event to be processed next
   * @return true to mark the event for processing; false to skip it
   * @throws RuntimeException to signal that processing cannot continue
   */
  boolean applies(RecordMetadata metadata);

  default MetadataFilter and(final MetadataFilter other) {
    Objects.requireNonNull(other);
    return (e) -> applies(e) && other.applies(e);
  }
}
